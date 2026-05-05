package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.annot.Constants.VERSION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.METHOD_POST;
import static com.predic8.membrane.core.http.Response.accepted;
import static com.predic8.membrane.core.http.Response.statusCode;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.mcp.McpSessionContext.McpSessionState.INITIALIZED;
import static com.predic8.membrane.core.interceptor.mcp.McpSessionContext.McpSessionState.READY;
import static com.predic8.membrane.core.jsonrpc.JSONRPCRequest.parse;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.*;

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

    private static final int MAX_EXCHANGES = 100;

    private static final Map<String, Object> EMPTY_OBJECT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false
    );
    private static final Map<String, Object> GET_EXCHANGES_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_EXCHANGES),
                    "includeBodies", Map.of("type", "boolean")
            ),
            "additionalProperties", false
    );

    private final Map<String, McpSessionContext> sessionContexts = new ConcurrentHashMap<>();
    private final McpPayloadSanitizer payloadSanitizer = new McpPayloadSanitizer();
    private final McpToolRegistry toolRegistry = buildToolRegistry();

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
                        "Gets recent HTTP exchanges with sanitized headers and optional bodies",
                        GET_EXCHANGES_SCHEMA,
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
        MCPUtil.rejectUnexpectedArguments(call, Set.of());
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
        MCPUtil.rejectUnexpectedArguments(call, Set.of());
        return MCPToolsCallResponse.from(call)
                .withJson(getRouter().getStatistics());
    }

    private MCPToolsCallResponse getExchanges(MCPToolsCall call, Exchange exc) {
        MCPUtil.rejectUnexpectedArguments(call, Set.of("limit", "includeBodies"));

        int limit = MCPUtil.getOptionalIntArgument(call, "limit", MAX_EXCHANGES, 1, MAX_EXCHANGES);
        boolean includeBodies = MCPUtil.getOptionalBooleanArgument(call, "includeBodies", false);

        List<AbstractExchange> exchanges = Optional.ofNullable(getRouter().getExchangeStore().getAllExchangesAsList())
                .orElse(List.of());
        int start = Math.max(0, exchanges.size() - limit);

        return MCPToolsCallResponse.from(call)
                .withJson(Map.of(
                        "exchanges",
                        exchanges.subList(start, exchanges.size()).stream()
                                .map(exchange -> MCPUtil.describeExchange(exchange, includeBodies, payloadSanitizer))
                                .filter(Objects::nonNull)
                                .toList()
                ));
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
