package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.mcp.MCPToolsCall;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.SOAPProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MCPUtil {

    private MCPUtil() {
    }

    public static Map<String, Object> describeProxy(Proxy proxy, Object statistics) {
        var description = new LinkedHashMap<String, Object>();
        description.put("name", proxy.getName());

        String type;
        switch (proxy) {
            case APIProxy ignored -> type = "API";
            case SOAPProxy soapProxy -> {
                type = "soapProxy";
                description.put("wsdl", soapProxy.getWsdl());
                description.put("serviceName", soapProxy.getServiceName());
            }
            case ServiceProxy ignored -> type = "serviceProxy";
            default -> type = "unknown";
        }

        description.put("type", type);
        description.put("rule", proxy.getKey().toString());
        description.put("interceptors", proxy.getFlow().stream()
                .map(interceptor -> Map.of("name", interceptor.getDisplayName()))
                .toList());
        description.put("statistics", statistics);
        return description;
    }

    public static @Nullable Map<String, Object> describeExchange(AbstractExchange exchange, boolean includeBodies, McpPayloadSanitizer payloadSanitizer) {
        if (exchange.getResponse() == null) {
            return null;
        }

        var description = new LinkedHashMap<String, Object>();
        description.put("id", exchange.getId());

        var request = new LinkedHashMap<String, Object>();
        request.put("method", exchange.getRequest().getMethod());
        request.put("path", exchange.getRequest().getUri());
        request.put("headers", payloadSanitizer.sanitizeHeaders(exchange.getRequest().getHeader()));
        if (includeBodies) {
            request.put("body", payloadSanitizer.sanitizeBody(exchange.getRequest()));
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("status", exchange.getResponse().getStatusCode());
        response.put("headers", payloadSanitizer.sanitizeHeaders(exchange.getResponse().getHeader()));
        if (includeBodies) {
            response.put("body", payloadSanitizer.sanitizeBody(exchange.getResponse()));
        }

        description.put("request", request);
        description.put("response", response);
        return description;
    }

    public static int getOptionalIntArgument(MCPToolsCall call, String name, int defaultValue, int minimum, int maximum) {
        Object value = call.getArgument(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new InvalidToolArgumentsException("Tool argument '" + name + "' must be an integer");
        }
        int parsed = number.intValue();
        if (parsed < minimum || parsed > maximum) {
            throw new InvalidToolArgumentsException(
                    "Tool argument '" + name + "' must be between " + minimum + " and " + maximum
            );
        }
        return parsed;
    }

    public static boolean getOptionalBooleanArgument(MCPToolsCall call, String name, boolean defaultValue) {
        Object value = call.getArgument(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new InvalidToolArgumentsException("Tool argument '" + name + "' must be a boolean");
    }

    public static void rejectUnexpectedArguments(MCPToolsCall call, Set<String> allowed) {
        for (String argumentName : call.getArguments().keySet()) {
            if (!allowed.contains(argumentName)) {
                throw new InvalidToolArgumentsException("Unexpected tool argument: " + argumentName);
            }
        }
    }

    public static final class InvalidToolArgumentsException extends IllegalArgumentException {
        private InvalidToolArgumentsException(String message) {
            super(message);
        }
    }
}
