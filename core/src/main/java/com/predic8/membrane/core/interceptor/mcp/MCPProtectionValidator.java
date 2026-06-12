package com.predic8.membrane.core.interceptor.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;
import com.predic8.membrane.core.mcp.MCPInitialize;
import com.predic8.membrane.core.mcp.MCPPing;
import com.predic8.membrane.core.mcp.MCPToolsCall;
import com.predic8.membrane.core.mcp.MCPToolsList;
import com.predic8.membrane.core.util.config.allowdeny.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.jsonrpc.JSONRPCRequest.fromNode;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_PARAMS;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_INVALID_REQUEST;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_METHOD_NOT_FOUND;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ERR_PARSE_ERROR;
import static com.predic8.membrane.core.mcp.MCPToolsCall.from;

final class MCPProtectionValidator {

    private static final Logger log = LoggerFactory.getLogger(MCPProtectionValidator.class);
    private static final ObjectMapper OM = new ObjectMapper();

    private final MCPProtectionMethods methods;
    private final List<Rule> toolRules;

    MCPProtectionValidator(MCPProtectionMethods methods, List<Rule> toolRules) {
        this.methods = methods == null ? new MCPProtectionMethods() : methods;
        this.toolRules = toolRules == null ? List.of() : toolRules;
    }

    ValidationError validate(String body) {
        if (body == null || body.isBlank()) {
            return invalidRequest("MCP request body must not be empty.");
        }

        JsonNode root;
        try {
            root = OM.readTree(body);
        } catch (JsonProcessingException e) {
            log.debug("Invalid MCP JSON payload.", e);
            return new ValidationError(null, 400, ERR_PARSE_ERROR, "Invalid JSON payload.");
        }

        if (root == null || !root.isObject()) {
            if (root != null && root.isArray()) {
                return invalidRequest("MCP does not support JSON-RPC batch requests.");
            }
            return invalidRequest("MCP payload must be a JSON-RPC request object.");
        }

        JSONRPCRequest request;
        try {
            request = fromNode(root);
        } catch (IOException | RuntimeException e) {
            return invalidRequest("Invalid JSON-RPC request: " + e.getMessage());
        }

        if (!isMethodAllowed(request.getMethod())) {
            return new ValidationError(
                    responseId(request),
                    403,
                    ERR_METHOD_NOT_FOUND,
                    "MCP method '%s' is not allowed.".formatted(request.getMethod())
            );
        }

        if (!MCPToolsCall.METHOD.equals(request.getMethod())) {
            return null;
        }

        MCPToolsCall toolsCall;
        try {
            toolsCall = from(request);
        } catch (IllegalArgumentException e) {
            return new ValidationError(
                    responseId(request),
                    400,
                    ERR_INVALID_PARAMS,
                    e.getMessage()
            );
        }

        if (isPermitted(toolsCall.getName(), toolRules)) {
            return null;
        }

        return new ValidationError(
                responseId(request),
                403,
                ERR_INVALID_PARAMS,
                "MCP tool '%s' is not allowed.".formatted(toolsCall.getName())
        );
    }

    private boolean isPermitted(String value, List<Rule> rules) {
        for (Rule rule : rules) {
            if (rule.matches(value)) {
                return rule.permits();
            }
        }
        return true;
    }

    private boolean isMethodAllowed(String method) {
        return switch (method) {
            case MCPInitialize.METHOD, MCPPing.METHOD -> true;
            case MCPToolsList.METHOD -> methods.isToolsList();
            case MCPToolsCall.METHOD -> methods.isToolsCall();
            default -> methods.isNotifications() && method.startsWith("notifications/");
        };
    }

    private ValidationError invalidRequest(String message) {
        return new ValidationError(null, 400, ERR_INVALID_REQUEST, message);
    }

    private Object responseId(JSONRPCRequest request) {
        return request.isNotification() ? null : request.getId();
    }

    record ValidationError(
            Object responseId,
            int httpStatus,
            int code,
            String message
    ) {
    }
}
