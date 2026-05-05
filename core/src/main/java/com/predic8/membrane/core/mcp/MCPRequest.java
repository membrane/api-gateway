package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for all MCP requests (JSON-RPC calls that carry an {@code id}
 * and expect a response).
 *
 * <p>Subclasses declare their {@code METHOD} constant and extract any
 * method-specific parameters in their own constructor:</p>
 * <pre>{@code
 * public class MCPToolsList extends MCPRequest {
 *     public static final String METHOD = "tools/list";
 *
 *     public MCPToolsList(JSONRPCRequest request) {
 *         super(request, METHOD);
 *         // extract params...
 *     }
 * }
 * }</pre>
 *
 * @see MCPNotification for notifications (no {@code id}, no response)
 * @see MCPResponse     for the corresponding response side
 */
public abstract class MCPRequest {

    private final Object id;

    /**
     * Validates the JSON-RPC request and extracts the {@code id}.
     *
     * @param request        the raw JSON-RPC 2.0 request
     * @param expectedMethod the MCP method this class handles (e.g. {@code "tools/list"})
     * @throws IllegalArgumentException if the method name does not match
     */
    protected MCPRequest(JSONRPCRequest request, String expectedMethod) {
        requireNonNull(request, "request must not be null");
        requireNonNull(expectedMethod, "expectedMethod must not be null");

        if (!expectedMethod.equals(request.getMethod())) {
            throw new IllegalArgumentException(
                    "Expected JSON-RPC method '" + expectedMethod
                    + "' but got '" + request.getMethod() + "'");
        }
        if (request.isNotification()) {
            throw new IllegalArgumentException(
                    "'" + expectedMethod + "' must be a request with an 'id', not a notification");
        }

        this.id = request.getId();
    }

    // ---------- Accessors ----------

    /** Returns the JSON-RPC {@code id} of this request (String, Number, or null). */
    public Object getId() {
        return id;
    }

    /** Convenience accessor — returns the id as String, or null. */
    public String getIdAsString() {
        return id == null ? null : id.toString();
    }
}
