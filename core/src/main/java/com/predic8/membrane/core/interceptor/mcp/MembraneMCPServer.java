package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.mcp.MCPUtil.InvalidToolArgumentsException;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.mcp.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.annot.Constants.VERSION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.http.Response.accepted;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.mcp.ExchangeUtils.matchesExchangeFilter;
import static com.predic8.membrane.core.interceptor.mcp.MCPUtil.*;
import static com.predic8.membrane.core.interceptor.mcp.McpSessionContext.McpSessionState.INITIALIZED;
import static com.predic8.membrane.core.interceptor.mcp.McpSessionContext.McpSessionState.READY;
import static com.predic8.membrane.core.jsonrpc.JSONRPCRequest.parse;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.*;
import static java.lang.Integer.MAX_VALUE;

/**
 * @description MCP Server for Membrane. It allows querying Membrane's internal state and operation from an LLM
 * Ask the LLM questions like:
 * - What APIs are deployed?
 * - Is the Membrane instance healthy?
 * - Give me a summary about the requests
 */
@MCElement(name = "membraneMCPServer")
public class MembraneMCPServer extends AbstractInterceptor {

    static final String SUPPORTED_PROTOCOL_VERSION = "2025-03-26";
    static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(MembraneMCPServer.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private static final int DEFAULT_MAX_EXCHANGES = 100;
    private static final String EXCHANGES_PAYLOAD_PREFIX = "{\"exchanges\":[";
    private static final String EXCHANGES_PAYLOAD_SUFFIX = "]}";
    private static final String EXCHANGES_PAYLOAD_SEPARATOR = ",";

    private static final Map<String, Object> EMPTY_OBJECT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false
    );
    private final Map<String, McpSessionContext> sessionContexts = new ConcurrentHashMap<>();
    private final McpPayloadSanitizer payloadSanitizer = new McpPayloadSanitizer();
    private int maxExchanges = DEFAULT_MAX_EXCHANGES;
    private McpToolRegistry toolRegistry = buildToolRegistry();

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            exc.setResponse(handleHttpRequest(exc));
            return RETURN;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Response handleHttpRequest(Exchange exc) throws IOException {
        if (!exc.getRequest().isPOSTRequest()) {
            return statusCode(405)
                    .header("Allow", METHOD_POST)
                    .bodyEmpty()
                    .build();
        }

        JSONRPCRequest request;
        try {
            request = parse(exc.getRequest().getBodyAsStreamDecoded());
        } catch (JsonProcessingException e) {
            return createResponse(badRequest(exc, null, ERR_PARSE_ERROR, "Parse error", e));
        } catch (IOException e) {
            return createResponse(badRequest(exc, null, ERR_INVALID_REQUEST, "Invalid request", e));
        }

        try {
            return createResponse(processMcpRequest(request, exc));
        } catch (IllegalArgumentException e) {
            return createResponse(badRequest(exc, request, ERR_INVALID_PARAMS, "Invalid params", e));
        } catch (Exception e) {
            return createResponse(internalError(exc, request, e));
        }
    }

    private McpHttpResult processMcpRequest(JSONRPCRequest request, Exchange exc) throws Exception {
        return switch (request.getMethod()) {
            case MCPInitialize.METHOD -> processInitialize(request, exc);
            case MCPInitialized.METHOD -> processInitializedNotification(request, exc);
            case MCPPing.METHOD -> processPing(request, exc);
            case MCPToolsList.METHOD -> processToolsList(request, exc);
            case MCPToolsCall.METHOD -> processToolsCall(request, exc);
            default -> protocolError(request, ERR_METHOD_NOT_FOUND, "Method not found");
        };
    }

    private McpHttpResult processInitialize(JSONRPCRequest request, Exchange exc) {
        MCPInitialize initialize = MCPInitialize.from(request);
        if (getSessionId(exc) != null) {
            return protocolError(request, ERR_INVALID_REQUEST, "'initialize' must not include '" + SESSION_HEADER + "'");
        }

        String sessionId = UUID.randomUUID().toString();
        McpSessionContext sessionContext = new McpSessionContext();
        if (!sessionContext.initialize(SUPPORTED_PROTOCOL_VERSION, initialize.getClientInfo())) {
            return protocolError(request, ERR_INVALID_REQUEST, "'initialize' must be the first MCP request");
        }
        sessionContexts.put(sessionId, sessionContext);

        return httpOk(new MCPInitializeResponse(initialize)
                .withProtocolVersion(SUPPORTED_PROTOCOL_VERSION)
                .withCapabilities(getCapabilities())
                .withServerInfo("Membrane", VERSION)
                .toRpcResponse(), Map.of(SESSION_HEADER, sessionId));
    }

    private McpHttpResult processInitializedNotification(JSONRPCRequest request, Exchange exc) {
        MCPInitialized.from(request);
        McpSessionContext sessionContext = requireSession(request, exc);
        if (sessionContext == null) {
            return missingOrInvalidSession(request, exc);
        }
        if (!sessionContext.markReady()) {
            return protocolError(
                    request,
                    ERR_INVALID_REQUEST,
                    "'notifications/initialized' is only allowed after a successful 'initialize'"
            );
        }
        log.debug("MCP client is ready");
        return acceptedNotification();
    }

    private McpHttpResult processPing(JSONRPCRequest request, Exchange exc) {
        MCPPing.from(request);
        McpSessionContext sessionContext = requireSession(request, exc);
        if (sessionContext == null) {
            return missingOrInvalidSession(request, exc);
        }
        if (!sessionContext.isIn(INITIALIZED, READY)) {
            return protocolError(request, ERR_INVALID_REQUEST, "'ping' is only allowed after 'initialize'");
        }
        return httpOk(success(request.getId(), Map.of()));
    }

    private McpHttpResult processToolsList(JSONRPCRequest request, Exchange exc) {
        MCPToolsList toolsList = MCPToolsList.from(request);
        McpSessionContext sessionContext = requireSession(request, exc);
        if (sessionContext == null) {
            return missingOrInvalidSession(request, exc);
        }
        if (!sessionContext.isIn(READY)) {
            return protocolError(request, ERR_INVALID_REQUEST, "'tools/list' requires a completed MCP handshake");
        }

        return httpOk(MCPToolsListResponse.from(toolsList)
                .withTools(toolRegistry.list().stream().map(McpToolDefinition::toTool).toList())
                .toRpcResponse());
    }

    private McpHttpResult processToolsCall(JSONRPCRequest request, Exchange exc) throws Exception {
        MCPToolsCall call = MCPToolsCall.from(request);
        McpSessionContext sessionContext = requireSession(request, exc);
        if (sessionContext == null) {
            return missingOrInvalidSession(request, exc);
        }
        if (!sessionContext.isIn(READY)) {
            return protocolError(request, ERR_INVALID_REQUEST, "'tools/call' requires a completed MCP handshake");
        }

        McpToolDefinition tool = toolRegistry.find(call.getName());
        if (tool == null) {
            return protocolError(request, ERR_INVALID_PARAMS, "Unknown tool: " + call.getName());
        }

        try {
            return httpOk(tool.handler().handle(call, exc).toRpcResponse());
        } catch (InvalidToolArgumentsException e) {
            return protocolError(request, ERR_INVALID_PARAMS, e.getMessage());
        } catch (IllegalArgumentException e) {
            return httpOk(MCPToolsCallResponse.toolError(call, e.getMessage()).toRpcResponse());
        }
    }

    private McpToolRegistry buildToolRegistry() {
        return new McpToolRegistry()
                .register(new McpToolDefinition(
                        "listProxies",
                        "Lists configured proxies and selected runtime metadata",
                        EMPTY_OBJECT_SCHEMA,
                        this::listProxies
                ))
                .register(new McpToolDefinition(
                        "getExchanges",
                        "Gets recent HTTP exchanges with sanitized headers, optional bodies, and request filters",
                        getExchangesSchema(),
                        this::getExchanges
                ))
                .register(new McpToolDefinition(
                        "getStatistics",
                        "Gets Membrane runtime statistics",
                        EMPTY_OBJECT_SCHEMA,
                        this::getStatistics
                ));
    }

    private MCPToolsCallResponse listProxies(MCPToolsCall call, Exchange exc) {
        rejectUnexpectedArguments(call, Set.of());
        return MCPToolsCallResponse.from(call)
                .withJson(Map.of(
                        "proxies",
                        getRouter().getRuleManager().getRules().stream()
                                .map(proxy -> MCPUtil.describeProxy(
                                        proxy,
                                        getRouter().getExchangeStore().getStatistics(proxy.getKey())
                                ))
                                .toList()
                ));
    }

    private MCPToolsCallResponse getStatistics(MCPToolsCall call, Exchange exc) {
        rejectUnexpectedArguments(call, Set.of());
        return MCPToolsCallResponse.from(call)
                .withJson(getRouter().getStatistics());
    }

    private MCPToolsCallResponse getExchanges(MCPToolsCall call, Exchange exc) {
        rejectUnexpectedArguments(call, Set.of("limit", "includeBodies", "host", "port", "pathPattern", "maxResponseSize"));

        // do not inline: args must be validated fist
        String host = getOptionalStringArgument(call, "host");
        Integer port = getOptionalPort(call);
        String pathPattern = getOptionalStringArgument(call, "pathPattern");
        int limit = getOptionalIntArgument(call, "limit", maxExchanges, 1, maxExchanges);
        boolean includeBodies = getOptionalBooleanArgument(call, "includeBodies", false);
        Integer maxResponseSize = getOptionalMaxResponseSize(call);

        List<AbstractExchange> exchanges = getRecentMatchingExchanges(host, port, pathPattern, limit);

        if (maxResponseSize == null) {
            return MCPToolsCallResponse.from(call)
                    .withJson(Map.of(
                            "exchanges",
                            exchanges.stream()
                                    .map(exchange -> MCPUtil.describeExchange(exchange, includeBodies, payloadSanitizer))
                                    .filter(Objects::nonNull)
                                    .toList()
                    ));
        }

        return MCPToolsCallResponse.from(call)
                .withText(buildLimitedExchangesPayload(call, exchanges, includeBodies, maxResponseSize));
    }

    private static @Nullable Integer getOptionalPort(MCPToolsCall call) {
        return call.getArgument("port") == null ? null : getOptionalIntArgument(call, "port", -1, 1, 65535);
    }

    private static @Nullable Integer getOptionalMaxResponseSize(MCPToolsCall call) {
        return call.getArgument("maxResponseSize") == null ? null : getOptionalSizeArgument(call, "maxResponseSize", -1, 1, MAX_VALUE);
    }

    private Map<String, Object> getExchangesSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", maxExchanges),
                        "includeBodies", Map.of("type", "boolean"),
                        "host", Map.of("type", "string"),
                        "port", Map.of("type", "integer", "minimum", 1, "maximum", 65535),
                        "pathPattern", Map.of("type", "string", "description", "Matches by prefix or regex"),
                        "maxResponseSize", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "description", "Maximum size in bytes of the final JSON-RPC response body returned by this tool"
                        )
                ),
                "additionalProperties", false
        );
    }

    private List<AbstractExchange> getRecentMatchingExchanges(@Nullable String host, @Nullable Integer port, @Nullable String pathPattern, int limit) {
        Deque<AbstractExchange> recentMatches = new ArrayDeque<>(limit);
        for (AbstractExchange exchange : Optional.ofNullable(getRouter().getExchangeStore().getAllExchangesAsList()).orElse(List.of())) {
            if (!matchesExchangeFilter(exchange, host, port, pathPattern)) {
                continue;
            }
            if (recentMatches.size() == limit) {
                recentMatches.removeFirst();
            }
            recentMatches.addLast(exchange);
        }
        return new ArrayList<>(recentMatches);
    }

    private String buildLimitedExchangesPayload(MCPToolsCall call, List<AbstractExchange> exchanges, boolean includeBodies, int maxResponseSize) {
        int fixedBytes = createTextResponseEnvelope(call).fixedBytes();
        int payloadBytes = escapedJsonStringContentSize(EXCHANGES_PAYLOAD_PREFIX) + escapedJsonStringContentSize(EXCHANGES_PAYLOAD_SUFFIX);
        int separatorBytes = escapedJsonStringContentSize(EXCHANGES_PAYLOAD_SEPARATOR);
        int minimumResponseSize = fixedBytes + payloadBytes;

        if (minimumResponseSize > maxResponseSize) {
            throw new InvalidToolArgumentsException(
                    "Tool argument 'maxResponseSize' must be at least " + minimumResponseSize + " bytes"
            );
        }

        Deque<String> serializedExchanges = new ArrayDeque<>();
        for (int i = exchanges.size() - 1; i >= 0; i--) {
            String exchangeJson = serializeExchange(exchanges.get(i), includeBodies);
            if (exchangeJson == null) {
                continue;
            }

            int additionalBytes = escapedJsonStringContentSize(exchangeJson) + (serializedExchanges.isEmpty() ? 0 : separatorBytes);
            if (fixedBytes + payloadBytes + additionalBytes > maxResponseSize) {
                break;
            }

            serializedExchanges.addFirst(exchangeJson);
            payloadBytes += additionalBytes;
        }

        return renderExchangesPayload(serializedExchanges);
    }

    private String renderExchangesPayload(Deque<String> serializedExchanges) {
        StringBuilder payload = new StringBuilder();
        payload.append(EXCHANGES_PAYLOAD_PREFIX);
        Iterator<String> iterator = serializedExchanges.iterator();
        while (iterator.hasNext()) {
            payload.append(iterator.next());
            if (iterator.hasNext()) {
                payload.append(EXCHANGES_PAYLOAD_SEPARATOR);
            }
        }
        payload.append(EXCHANGES_PAYLOAD_SUFFIX);
        return payload.toString();
    }

    // Measure the fixed JSON-RPC/MCP wrapper once with a placeholder so the byte limit
    // applies to the final serialized response body, not just the unescaped payload text.
    private TextResponseEnvelope createTextResponseEnvelope(MCPToolsCall call) {
        String marker = "__MEMBRANE_MCP_TEXT_PLACEHOLDER_" + UUID.randomUUID() + "__";
        try {
            String responseJson = MCPToolsCallResponse.from(call).withText(marker).toJson();
            int markerIndex = responseJson.indexOf(marker);
            if (markerIndex < 0) {
                throw new IllegalStateException("Could not locate placeholder marker in serialized MCP response");
            }

            return new TextResponseEnvelope(
                    utf8Size(responseJson.substring(0, markerIndex)),
                    utf8Size(responseJson.substring(markerIndex + marker.length()))
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP response envelope", e);
        }
    }

    private @Nullable String serializeExchange(AbstractExchange exchange, boolean includeBodies) {
        Map<String, Object> description = MCPUtil.describeExchange(exchange, includeBodies, payloadSanitizer);
        if (description == null) {
            return null;
        }
        try {
            return OM.writeValueAsString(description);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize exchange data to JSON", e);
        }
    }

    private static int utf8Size(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private int escapedJsonStringContentSize(String value) {
        try {
            return OM.writeValueAsBytes(value).length - 2;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON string content", e);
        }
    }

    private record TextResponseEnvelope(int prefixBytes, int suffixBytes) {
        private int fixedBytes() {
            return prefixBytes + suffixBytes;
        }
    }

    public int getMaxExchanges() {
        return maxExchanges;
    }

    /**
     * @description Maximum number of exchanges that can be returned by the getExchanges MCP tool.
     * @default 100
     */
    @MCAttribute
    public void setMaxExchanges(int maxExchanges) {
        if (maxExchanges < 1) {
            throw new IllegalArgumentException("maxExchanges must be >= 1");
        }
        this.maxExchanges = maxExchanges;
        toolRegistry = buildToolRegistry();
    }

    private McpHttpResult badRequest(Exchange exc, @Nullable JSONRPCRequest request, int code, String message, Exception exception) {
        log.info("Rejected MCP request {} {}: {}", exc.getRequest().getMethod(), exc.getRequest().getUri(), exception.getMessage());
        return new McpHttpResult(400, error(responseId(request), code, message, exception.getMessage()), Map.of());
    }

    private McpHttpResult internalError(Exchange exc, @Nullable JSONRPCRequest request, Exception exception) {
        log.warn("Failed to handle MCP request {} {}.", exc.getRequest().getMethod(), exc.getRequest().getUri(), exception);
        return new McpHttpResult(
                request != null && request.isNotification() ? 500 : 200,
                error(responseId(request), ERR_INTERNAL_ERROR, "Internal error", exception.getMessage()),
                Map.of()
        );
    }

    private McpHttpResult protocolError(JSONRPCRequest request, int code, String message) {
        return protocolError(request, code, message, null);
    }

    private McpHttpResult protocolError(JSONRPCRequest request, int code, String message, @Nullable Object data) {
        return new McpHttpResult(
                request.isNotification() ? 400 : 200,
                data == null ? error(responseId(request), code, message) : error(responseId(request), code, message, data),
                Map.of()
        );
    }

    private @Nullable McpSessionContext requireSession(JSONRPCRequest request, Exchange exc) {
        String sessionId = getSessionId(exc);
        if (sessionId == null) {
            return null;
        }
        return sessionContexts.get(sessionId);
    }

    private McpHttpResult missingOrInvalidSession(JSONRPCRequest request, Exchange exc) {
        String sessionId = getSessionId(exc);
        if (sessionId == null) {
            return new McpHttpResult(
                    400,
                    error(responseId(request), ERR_INVALID_REQUEST, "'" + SESSION_HEADER + "' header is required"),
                    Map.of()
            );
        }
        return new McpHttpResult(
                404,
                error(responseId(request), ERR_INVALID_REQUEST, "Unknown MCP session"),
                Map.of()
        );
    }

    private @Nullable String getSessionId(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(SESSION_HEADER);
    }

    private static Object responseId(@Nullable JSONRPCRequest request) {
        if (request == null || request.isNotification()) {
            return null;
        }
        return request.getId();
    }

    private static McpHttpResult httpOk(JSONRPCResponse response) {
        return httpOk(response, Map.of());
    }

    private static McpHttpResult httpOk(JSONRPCResponse response, Map<String, String> headers) {
        return new McpHttpResult(200, response, headers);
    }

    private static McpHttpResult acceptedNotification() {
        return new McpHttpResult(202, null, Map.of());
    }

    private static Response createResponse(McpHttpResult result) throws IOException {
        if (result.body() == null) {
            if (result.status() == 202) {
                return accepted().build();
            }
            return statusCode(result.status()).bodyEmpty().build();
        }
        Response.ResponseBuilder builder = statusCode(result.status())
                .contentType(APPLICATION_JSON);
        for (Map.Entry<String, String> header : result.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.body(result.body().toJson()).build();
    }

    private static Map<String, Object> getCapabilities() {
        return Map.of("tools", Map.of("listChanged", false));
    }

    @Override
    public String getDisplayName() {
        return "Membrane MCP Server";
    }

    record McpHttpResult(
            int status,
            @Nullable JSONRPCResponse body,
            Map<String, String> headers
    ) {
    }
}
