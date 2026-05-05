package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.Objects;

/**
 * Abstract base class for all MCP notifications (JSON-RPC messages without an
 * {@code id} that require no response).
 *
 * <p>Subclasses declare their {@code METHOD} constant and may extract optional
 * notification-specific parameters in their own constructor:</p>
 * <pre>{@code
 * public class MCPInitialized extends MCPNotification {
 *     public static final String METHOD = "notifications/initialized";
 *
 *     public MCPInitialized(JSONRPCRequest request) {
 *         super(request, METHOD);
 *     }
 * }
 * }</pre>
 *
 * @see MCPRequest  for requests that carry an {@code id} and expect a response
 */
public abstract class MCPNotification {

    /**
     * Validates the JSON-RPC request as a proper notification.
     *
     * @param request        the raw JSON-RPC 2.0 request
     * @param expectedMethod the MCP method this class handles (e.g. {@code "notifications/initialized"})
     * @throws IllegalArgumentException if the method name does not match or the
     *                                  request carries an {@code id} (making it a
     *                                  request rather than a notification)
     */
    protected MCPNotification(JSONRPCRequest request, String expectedMethod) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(expectedMethod, "expectedMethod must not be null");

        if (!expectedMethod.equals(request.getMethod())) {
            throw new IllegalArgumentException(
                    "Expected JSON-RPC method '" + expectedMethod
                    + "' but got '" + request.getMethod() + "'");
        }
        if (!request.isNotification()) {
            throw new IllegalArgumentException(
                    "'" + expectedMethod + "' must be a notification (no 'id')"
                    + ", but id was: " + request.getId());
        }
    }
}
