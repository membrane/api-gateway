package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.mcp.*;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class MembraneMCPServerTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String POST_ACCEPT_HEADER = "application/json, text/event-stream";
    private static final String GET_ACCEPT_HEADER = "text/event-stream";

    @Test
    void toolsListBeforeInitializeIsRejected() throws Exception {
        Exchange exc = invoke(newServer(), request(1, MCPToolsList.METHOD, Map.of()));

        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals(-32600, responseJson(exc).path("error").path("code").asInt());
    }

    @Test
    void initializedNotificationBeforeInitializeIsRejected() throws Exception {
        Exchange exc = invoke(newServer(), notification(MCPInitialized.METHOD, Map.of()));

        assertEquals(400, exc.getResponse().getStatusCode());
        if (!exc.getResponse().isBodyEmpty()) {
            assertEquals(-32600, responseJson(exc).path("error").path("code").asInt());
        }
    }

    @Test
    void pingBeforeInitializeIsRejected() throws Exception {
        Exchange exc = invoke(newServer(), request(1, MCPPing.METHOD, Map.of()));

        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals(-32600, responseJson(exc).path("error").path("code").asInt());
    }

    @Test
    void toolsListAfterInitializeButBeforeInitializedIsRejected() throws Exception {
        MembraneMCPServer server = newServer();

        Exchange initialize = invoke(server, initializeRequest("2025-03-26"));
        assertEquals(200, initialize.getResponse().getStatusCode());
        assertEquals(MembraneMCPServer.SUPPORTED_PROTOCOL_VERSION, responseJson(initialize).path("result").path("protocolVersion").asText());
        String sessionId = sessionId(initialize);

        Exchange toolsList = invoke(server, request(2, MCPToolsList.METHOD, Map.of()), sessionId);
        assertEquals(200, toolsList.getResponse().getStatusCode());
        assertEquals(-32600, responseJson(toolsList).path("error").path("code").asInt());
    }

    @Test
    void initializePingInitializedAndToolsListFollowTheLifecycle() throws Exception {
        MembraneMCPServer server = newServer();

        Exchange initialize = invoke(server, initializeRequest("2025-03-26"));
        assertEquals(200, initialize.getResponse().getStatusCode());
        JsonNode initializeJson = responseJson(initialize);
        assertEquals(MembraneMCPServer.SUPPORTED_PROTOCOL_VERSION, initializeJson.path("result").path("protocolVersion").asText());
        String sessionId = sessionId(initialize);

        Exchange ping = invoke(server, request(2, MCPPing.METHOD, Map.of()), sessionId);
        assertEquals(200, ping.getResponse().getStatusCode());
        assertTrue(responseJson(ping).path("result").isObject());
        assertEquals(0, responseJson(ping).path("result").size());

        Exchange initialized = invoke(server, notification(MCPInitialized.METHOD, Map.of()), sessionId);
        assertEquals(202, initialized.getResponse().getStatusCode());
        assertEquals("", initialized.getResponse().getBodyAsStringDecoded());

        Exchange toolsList = invoke(server, request(3, MCPToolsList.METHOD, Map.of()), sessionId);
        JsonNode tools = responseJson(toolsList).path("result").path("tools");
        JsonNode getExchangesTool = findToolByName(tools, "getExchanges");

        assertNotNull(findToolByName(tools, "listProxies"));
        assertNotNull(getExchangesTool);
        assertNotNull(findToolByName(tools, "getStatistics"));
        assertTrue(getExchangesTool.path("inputSchema").path("properties").has("limit"));
        assertFalse(getExchangesTool.path("inputSchema").path("additionalProperties").asBoolean(true));
    }

    @Test
    void initializeCreatesIndependentSessions() throws Exception {
        MembraneMCPServer server = newServer();

        Exchange firstInitialize = invoke(server, initializeRequest("2025-03-26"));
        Exchange secondInitialize = invoke(server, initializeRequest("2025-03-26"));

        assertEquals(200, firstInitialize.getResponse().getStatusCode());
        assertEquals(200, secondInitialize.getResponse().getStatusCode());
        assertNotEquals(sessionId(firstInitialize), sessionId(secondInitialize));
    }

    @Test
    void toolsListWithoutSessionHeaderIsRejectedEvenAfterInitialize() throws Exception {
        MembraneMCPServer server = newServer();

        Exchange initialize = invoke(server, initializeRequest("2025-03-26"));
        assertEquals(200, initialize.getResponse().getStatusCode());

        Exchange toolsList = invoke(server, request(2, MCPToolsList.METHOD, Map.of()));
        assertEquals(400, toolsList.getResponse().getStatusCode());
        assertEquals(-32600, responseJson(toolsList).path("error").path("code").asInt());
    }

    @Test
    void unknownToolReturnsJsonRpcError() throws Exception {
        ReadyServer server = readyServer();

        Exchange exc = invoke(server.server(), request(
                2,
                MCPToolsCall.METHOD,
                Map.of("name", "doesNotExist", "arguments", Map.of())
        ), server.sessionId());

        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals(-32602, responseJson(exc).path("error").path("code").asInt());
    }

    @Test
    void invalidToolArgumentsReturnJsonRpcError() throws Exception {
        ReadyServer server = readyServer();

        Exchange exc = invoke(server.server(), request(
                2,
                MCPToolsCall.METHOD,
                Map.of("name", "getExchanges", "arguments", Map.of("limit", 0))
        ), server.sessionId());

        assertEquals(200, exc.getResponse().getStatusCode());
        JsonNode response = responseJson(exc);
        assertEquals(-32602, response.path("error").path("code").asInt());
    }

    @Test
    void getWithoutSseReturns405AndAllowPost() throws Exception {
        MembraneMCPServer server = newServer();
        Exchange exc = Request.get("http://localhost/mcp")
                .header("Accept", GET_ACCEPT_HEADER)
                .buildExchange();

        assertEquals(Outcome.RETURN, server.handleRequest(exc));
        assertEquals(405, exc.getResponse().getStatusCode());
        assertEquals(Request.METHOD_POST, exc.getResponse().getHeader().getFirstValue("Allow"));
    }

    @Test
    void unsupportedProtocolVersionNegotiatesServerVersion() throws Exception {
        Exchange exc = invoke(newServer(), initializeRequest("2024-11-05"));

        assertEquals(200, exc.getResponse().getStatusCode());
        JsonNode response = responseJson(exc);
        assertTrue(response.path("error").isMissingNode());
        assertEquals(MembraneMCPServer.SUPPORTED_PROTOCOL_VERSION, response.path("result").path("protocolVersion").asText());
    }

    @Test
    void getExchangesRedactsSensitiveHeadersAndOmitsBinaryBodies() throws Exception {
        MembraneMCPServer server = newServer(new StubExchangeStore(List.of(sampleExchange())));
        String sessionId = readyHandshake(server);

        Exchange exc = invoke(server, request(
                2,
                MCPToolsCall.METHOD,
                Map.of("name", "getExchanges", "arguments", Map.of("limit", 1, "includeBodies", true))
        ), sessionId);

        JsonNode payload = toolPayload(exc);
        JsonNode exchange = payload.path("exchanges").get(0);
        Map<String, String> requestHeaders = normalizeHeaders(exchange.path("request").path("headers"));
        Map<String, String> responseHeaders = normalizeHeaders(exchange.path("response").path("headers"));

        assertEquals("<redacted>", requestHeaders.get("authorization"));
        assertEquals("<redacted>", requestHeaders.get("cookie"));
        assertEquals("<redacted>", responseHeaders.get("set-cookie"));
        assertEquals("<binary body omitted>", exchange.path("response").path("body").asText());
    }

    private static MembraneMCPServer newServer() {
        return newServer(new ForgetfulExchangeStore());
    }

    private static MembraneMCPServer newServer(ForgetfulExchangeStore exchangeStore) {
        TestRouter router = new TestRouter();
        router.setExchangeStore(exchangeStore);
        router.init();

        MembraneMCPServer server = new MembraneMCPServer();
        server.init(router);
        return server;
    }

    private static ReadyServer readyServer() throws Exception {
        MembraneMCPServer server = newServer();
        return new ReadyServer(server, readyHandshake(server));
    }

    private static String readyHandshake(MembraneMCPServer server) throws Exception {
        Exchange initialize = invoke(server, initializeRequest("2025-03-26"));
        assertEquals(200, initialize.getResponse().getStatusCode());
        assertEquals(MembraneMCPServer.SUPPORTED_PROTOCOL_VERSION, responseJson(initialize).path("result").path("protocolVersion").asText());
        String sessionId = sessionId(initialize);
        assertNotNull(sessionId);

        Exchange initialized = invoke(server, notification(MCPInitialized.METHOD, Map.of()), sessionId);
        assertEquals(202, initialized.getResponse().getStatusCode());
        assertEquals("", initialized.getResponse().getBodyAsStringDecoded());
        return sessionId;
    }

    private static JSONRPCRequest initializeRequest(String protocolVersion) {
        return request(
                1,
                MCPInitialize.METHOD,
                Map.of(
                        "protocolVersion", protocolVersion,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "test-client", "version", "1.0.0")
                )
        );
    }

    private static JSONRPCRequest request(Object id, String method, Map<String, Object> params) {
        return new JSONRPCRequest(id, method, params);
    }

    private static JSONRPCRequest notification(String method, Map<String, Object> params) {
        return new JSONRPCRequest(null, false, method, params);
    }

    private static Exchange invoke(MembraneMCPServer server, JSONRPCRequest request) throws Exception {
        return invoke(server, request, null);
    }

    private static Exchange invoke(MembraneMCPServer server, JSONRPCRequest request, String sessionId) throws Exception {
        Request.Builder builder = Request.post("http://localhost/mcp")
                .header("Accept", POST_ACCEPT_HEADER);
        if (sessionId != null) {
            builder.header(MembraneMCPServer.SESSION_HEADER, sessionId);
        }
        Exchange exc = builder
                .json(request.toJson())
                .buildExchange();

        assertEquals(Outcome.RETURN, server.handleRequest(exc));
        return exc;
    }

    private static String sessionId(Exchange exc) {
        return exc.getResponse().getHeader().getFirstValue(MembraneMCPServer.SESSION_HEADER);
    }

    private static JsonNode responseJson(Exchange exc) throws Exception {
        return OM.readTree(exc.getResponse().getBodyAsStringDecoded());
    }

    private static JsonNode toolPayload(Exchange exc) throws Exception {
        JsonNode textNode = responseJson(exc)
                .path("result")
                .path("content")
                .get(0)
                .path("text");
        return OM.readTree(textNode.asText());
    }

    private static JsonNode findToolByName(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        return null;
    }

    private static Map<String, String> normalizeHeaders(JsonNode headersNode) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, JsonNode> entry : headersNode.properties()) {
            headers.put(entry.getKey().toLowerCase(), entry.getValue().asText());
        }
        return headers;
    }

    private static Exchange sampleExchange() throws URISyntaxException {
        Exchange exc = new Exchange(null);
        exc.setRequest(Request.post("http://localhost/internal")
                .contentType("application/json")
                .header("Authorization", "Bearer super-secret")
                .header("Cookie", "session=top-secret")
                .body("{\"secret\":true}")
                .build());
        exc.setResponse(Response.ok()
                .contentType("image/png")
                .header("Set-Cookie", "session=top-secret")
                .body(new byte[]{1, 2, 3, 4})
                .build());
        return exc;
    }

    private static final class StubExchangeStore extends ForgetfulExchangeStore {
        private final List<AbstractExchange> exchanges;

        private StubExchangeStore(List<AbstractExchange> exchanges) {
            this.exchanges = exchanges;
        }

        @Override
        public List<AbstractExchange> getAllExchangesAsList() {
            return exchanges;
        }
    }

    private record ReadyServer(MembraneMCPServer server, String sessionId) {
    }
}
