package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.annot.Constants;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.mcp.*;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.SOAPProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

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

    @Override
    public Outcome handleRequest(Exchange exc) {

        try {
            var request = JSONRPCRequest.parse(exc.getRequest().getBodyAsStreamDecoded());

            MCPResponse<?> mcpResponse = null;
            Response response;
            if (request.getMethod().equals("initialize")) {
                mcpResponse = initialize(exc, request);
            } else if (request.getMethod().equals("notifications/initialized")) {
                // Do nothing
            } else if (request.getMethod().equals("tools/list")) {
                mcpResponse = toolsList(exc, request);
            } else if (request.getMethod().equals("tools/call")) {
                mcpResponse = toolsCall(exc, request);
            } else {
                System.out.println("Unknown MCP Request: " + request);
            }
            if (mcpResponse == null) {
                response = Response.noContent().build();
            } else {
                response = ok().contentType(APPLICATION_JSON).body(mcpResponse.toJson()).build();
            }
            exc.setResponse(response);
            return RETURN;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MCPResponse toolsCall(Exchange exc, JSONRPCRequest request) throws IOException {
        var req = MCPToolsCall.from(request);

        log.debug("Received MCP tools call: {}", req);

        if (req.getName().equals("listProxies")) {
            return listProxies(req);
        } else if (req.getName().equals("getExchanges")) {
            return getExchanges(req);
        } else if (req.getName().equals("getStatistics")) {
            getRouter().getStatistics();
        } else {
            log.info("Unknown tools call: " + req.getName());
        }

        return null;
    }

    private MCPToolsCallResponse getExchanges(MCPToolsCall req) {
        var exchangesRes = getRouter().getExchangeStore().getAllExchangesAsList().stream().map(e -> {

            if (e.getResponse() == null)
                return null;

            var exc = new HashMap<String, Object>();
            exc.put("id", e.getId());
            var request = new HashMap<String, Object>();
            var response = new HashMap<String, Object>();

            request.put("method", e.getRequest().getMethod());
            request.put("path", e.getRequest().getUri());
            request.put("body", e.getRequest().getBodyAsStringDecoded());
            request.put("headers",e.getRequest().getHeader());

            exc.put("request", request);
            exc.put("response", response);
            return exc;
        }).filter(Objects::nonNull).toList();

        return MCPToolsCallResponse.from(req).withJson(Map.of("exchanges", exchangesRes));
    }

    private MCPToolsCallResponse listProxies(MCPToolsCall req) {
        var proxies = getRouter().getRuleManager().getRules();

        var proxiesDesc = proxies.stream().map(p -> {
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
                Map<String, String> interceptor = new HashMap();
                interceptor.put("name", i.getDisplayName());
                return interceptor;
            }).toList();

            proxy.put("statistics", getRouter().getExchangeStore().getStatistics(p.getKey()));

            proxy.put("interceptors", interceptors);
            proxy.put("type", type);
            proxy.put("rule", p.getKey().toString());
            return proxy;
        }).toList();

        return MCPToolsCallResponse.from(req).withJson(Map.of("proxies", proxiesDesc));
    }

    private MCPResponse toolsList(Exchange exc, JSONRPCRequest request) throws IOException {
        log.debug("Tools list");
        var req = MCPToolsList.from(request);
        var resp = MCPToolsListResponse.from(req)
                .withTool(new MCPToolsListResponse.Tool(
                        "listProxies",
                        "Lists all the proxies, e.g. API, soapProxy", Map.of("type", "object")))
                .withTool(new MCPToolsListResponse.Tool("getExchanges", "Gets the last 100 HTTP exchanges", Map.of("type", "object")));

//                        Map.of("type", "object",
//                                "properties", Map.of("query", Map.of("type", "string")),
//                                "required", List.of("query"))));
        return resp;
    }

    private MCPResponse<?> initialize(Exchange exc, JSONRPCRequest request) throws IOException {
        var initialize = new MCPInitialize(request);

        log.debug("initialize: " + initialize);

        var response = new MCPInitializeResponse(initialize);

        var capabilities = new HashMap<String, Object>();
        capabilities.put("tools", Map.of("lastExchanges", false));
        response.withCapabilities(capabilities)
                .withServerInfo("Membrane", Constants.VERSION);
        return response;
    }

    @Override
    public String getDisplayName() {
        return "Membrane MCP Server";
    }
}
