package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;
import com.predic8.membrane.core.mcp.*;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.SOAPProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.predic8.membrane.annot.Constants.VERSION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.*;

/**
 * @description MCP Server for Membrane. It allows to query Membrane's internal state and operation from an LLM
 * Ask the LLM questions like:
 * - What APIs are deployed?
 * - Is the Membrane instance healthy?
 * - Give me a summary about the requests
 */
@MCElement(name = "membraneMCPServer")
public class MembraneMCPServer extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MembraneMCPServer.class);
    private static final int MAX_EXCHANGES = 100;

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            JSONRPCRequest request;
            try {
                request = JSONRPCRequest.parse(exc.getRequest().getBodyAsStreamDecoded());
            } catch (JsonProcessingException e) {
                exc.setResponse(createResponse(createErrorResponse(exc, null, ERR_PARSE_ERROR, "Parse error", e)));
                return RETURN;
            } catch (IOException e) {
                exc.setResponse(createResponse(createErrorResponse(exc, null, ERR_INVALID_REQUEST, "Invalid Request", e)));
                return RETURN;
            }

            JSONRPCResponse rpcResponse;
            try {
                rpcResponse = processMCPRequest(request);
            } catch (IllegalArgumentException e) {
                exc.setResponse(createResponse(createErrorResponse(exc, request, JSONRPCResponse.ERR_INVALID_PARAMS, "Invalid params", e)));
                return RETURN;
            } catch (Exception e) {
                exc.setResponse(createResponse(createErrorResponse(exc, request, ERR_INTERNAL_ERROR, "Internal error", e)));
                return RETURN;
            }
            exc.setResponse(createResponse(rpcResponse));
            return RETURN;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONRPCResponse createErrorResponse(Exchange exc, @Nullable JSONRPCRequest request, int code, String message, Exception e) {
        if (code == ERR_INTERNAL_ERROR) {
            log.warn("Failed to handle MCP request {} {}.", exc.getRequest().getMethod(), exc.getRequest().getUri(), e);
        } else {
            log.info("Rejected MCP request {} {}: {}", exc.getRequest().getMethod(), exc.getRequest().getUri(), e.getMessage());
        }
        return error(request == null ? null : request.getId(), code, message, e.getMessage());
    }

    private static Response createResponse(MCPResponse<?> mcpResponse) throws IOException {
        if (mcpResponse == null) {
            return Response.noContent().build();
        }
        return createResponse(mcpResponse.toRpcResponse());
    }

    private static Response createResponse(@Nullable JSONRPCResponse rpcResponse) throws IOException {
        if (rpcResponse == null) {
            return Response.noContent().build();
        }
        return ok().contentType(APPLICATION_JSON).body(rpcResponse.toJson()).build();
    }

    private @Nullable JSONRPCResponse processMCPRequest(JSONRPCRequest request) throws IOException {
        switch (request.getMethod()) {
            case "initialize" -> {
                return initialize(request).toRpcResponse();
            }
            case "notifications/initialized" -> {
                log.debug("MCP Client is ready");
                return null;
            }
            case "tools/list" -> {
                return toolsList(request).toRpcResponse();
            }
            case "tools/call" -> {
                var response = toolsCall(request);
                return response == null ? null : response.toRpcResponse();
            }
            default -> {
                log.info("Unknown MCP Request: {}", request);
                if (!request.isNotification()) {
                    return error(request.getId(), ERR_METHOD_NOT_FOUND, "Method not found");
                }
            }
        }
        return null;
    }

    private MCPToolsCallResponse toolsCall(JSONRPCRequest request) {
        var req = MCPToolsCall.from(request);

        log.debug("Received MCP tools call: {}", req);

        switch (req.getName()) {
            case "listProxies" -> {
                return listProxies(req);
            }
            case "getExchanges" -> {
                return getExchanges(req);
            }
            case "getStatistics" -> {
                return getStatistics(req);
            }
            default -> log.info("Unknown tools call: " + req.getName());
        }

        return null;
    }

    private MCPToolsCallResponse getStatistics(MCPToolsCall req) {
        return MCPToolsCallResponse.from(req)
                .withJson(getRouter().getStatistics());
    }

    private MCPToolsCallResponse getExchanges(MCPToolsCall req) {
        var exchanges = getRouter().getExchangeStore().getAllExchangesAsList();
        int start = Math.max(0, exchanges.size() - MAX_EXCHANGES);

        return MCPToolsCallResponse.from(req)
                .withJson(Map.of("exchanges", exchanges.subList(start, exchanges.size()).stream()
                        .map(MembraneMCPServer::getExchangeDescription)
                        .filter(Objects::nonNull).toList()));
    }

    private static @Nullable HashMap<String, Object> getExchangeDescription(AbstractExchange e) {
        if (e.getResponse() == null)
            return null;

        var exc = new HashMap<String, Object>();
        exc.put("id", e.getId());
        var request = new HashMap<String, Object>();
        var response = new HashMap<String, Object>();

        request.put("method", e.getRequest().getMethod());
        request.put("path", e.getRequest().getUri());
        request.put("body", e.getRequest().getBodyAsStringDecoded());
        request.put("headers", e.getRequest().getHeader());

        exc.put("request", request);
        exc.put("response", response);
        return exc;
    }

    private MCPToolsCallResponse listProxies(MCPToolsCall req) {
        return MCPToolsCallResponse.from(req)
                .withJson(Map.of("proxies", getRouter().getRuleManager().getRules().stream().map(this::getProxyDescription).toList()));
    }

    private @NotNull HashMap<String, Object> getProxyDescription(Proxy p) {
        var proxy = new HashMap<String, Object>();
        proxy.put("name", p.getName());

        String type;
        switch (p) {
            case APIProxy ap -> {
                type = "API";
                //     proxy.put("openapi", ap.getOpenapi());
            }
            case ServiceProxy s -> {
                type = "serviceProxy";
            }
            case SOAPProxy sp -> {
                type = "soapProxy";
                proxy.put("wsdl", sp.getWsdl());
                proxy.put("serviceName", sp.getServiceName());
            }
            default -> {
                type = "unknown";
            }
        }

        var interceptors = p.getFlow().stream().map(i -> {
            Map<String, String> interceptor = new HashMap<>();
            interceptor.put("name", i.getDisplayName());
            return interceptor;
        }).toList();

        proxy.put("statistics", getRouter().getExchangeStore().getStatistics(p.getKey()));

        proxy.put("interceptors", interceptors);
        proxy.put("type", type);
        proxy.put("rule", p.getKey().toString());
        return proxy;
    }

    private MCPToolsListResponse toolsList(JSONRPCRequest request) {
        log.debug("Tools list");
        return MCPToolsListResponse.from(MCPToolsList.from(request))
                .withTool(new MCPToolsListResponse.Tool(
                        "listProxies",
                        "Lists all the proxies, e.g. API, soapProxy", Map.of("type", "object")))
                .withTool(new MCPToolsListResponse.Tool("getExchanges", "Gets the last 100 HTTP exchanges", Map.of("type", "object")))
                .withTool(new MCPToolsListResponse.Tool("getStatistics", "Gets Membrane runtime statistics", Map.of("type", "object")));

        //                        Map.of("type", "object",
//                                "properties", Map.of("query", Map.of("type", "string")),
//                                "required", List.of("query"))));

    }

    private MCPInitializeResponse initialize(JSONRPCRequest request) throws IOException {
        return new MCPInitializeResponse(new MCPInitialize(request))
                .withCapabilities(getCapabilities())
                .withServerInfo("Membrane", VERSION);
    }

    private static @NotNull HashMap<String, Object> getCapabilities() {
        var capabilities = new HashMap<String, Object>();
        capabilities.put("tools", Map.of("listChanged", false));
        return capabilities;
    }

    @Override
    public String getDisplayName() {
        return "Membrane MCP Server";
    }
}
