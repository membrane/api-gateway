package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

/**
 * Typed view of the MCP {@code notifications/initialized} notification.
 *
 * <p>After the server has responded to {@code initialize}, the client sends this
 * notification to signal that it is ready to begin normal operations. The server
 * MUST NOT send any requests to the client before receiving this notification.</p>
 *
 * <p>Per JSON-RPC 2.0, notifications carry no {@code id} and require no response.</p>
 *
 * <p>Wire format:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "method":  "notifications/initialized",
 *   "params":  {}
 * }
 * }</pre>
 */
public class MCPInitialized extends MCPNotification {

    public static final String METHOD = "notifications/initialized";

    public MCPInitialized(JSONRPCRequest request) {
        super(request, METHOD);
    }

    /** Static factory equivalent to {@link #MCPInitialized(JSONRPCRequest)}. */
    public static MCPInitialized from(JSONRPCRequest request) {
        return new MCPInitialized(request);
    }

    @Override
    public String toString() {
        return "MCPInitialized{method='" + METHOD + "'}";
    }
}
