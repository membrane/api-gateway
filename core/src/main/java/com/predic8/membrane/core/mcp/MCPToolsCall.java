package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.Collections;
import java.util.Map;

/**
 * Typed view of an MCP {@code tools/call} request.
 *
 * <p>Wire format:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id":      2,
 *   "method":  "tools/call",
 *   "params": {
 *     "name":      "listProxies",
 *     "arguments": { "query": "..." }
 *   }
 * }
 * }</pre>
 *
 * <p>{@code arguments} is optional and defaults to an empty map if absent.</p>
 */
public class MCPToolsCall extends MCPRequest {

    public static final String METHOD = "tools/call";

    /** The name of the tool to invoke — matches {@code Tool.name} from {@code tools/list}. */
    private final String name;

    /** The tool's input arguments, keyed by parameter name. Empty map if none were provided. */
    private final Map<String, Object> arguments;

    public MCPToolsCall(JSONRPCRequest request) {
        super(request, METHOD);

        if (!request.hasNamedParams()) {
            throw new IllegalArgumentException(
                    "MCP 'tools/call' requires a params object (named parameters)");
        }

        Map<String, Object> params = request.getParamsMap();
        this.name = requireString(params, "name");
        this.arguments = extractArguments(params);
    }

    /** Static factory equivalent to {@link #MCPToolsCall(JSONRPCRequest)}. */
    public static MCPToolsCall from(JSONRPCRequest request) {
        return new MCPToolsCall(request);
    }

    // ---------- Helpers ----------

    private static String requireString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP 'tools/call' params: '" + key + "' must be a non-empty string");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractArguments(Map<String, Object> params) {
        Object args = params.get("arguments");
        if (args == null) return Collections.emptyMap();
        if (args instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException(
                "MCP 'tools/call' params: 'arguments' must be a JSON object");
    }

    // ---------- Accessors ----------

    /** Returns the name of the tool to invoke. */
    public String getName() {
        return name;
    }

    /** Returns the tool's input arguments, or an empty map if none were provided. */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /** Convenience: returns a single argument by key, or {@code null} if absent. */
    public Object getArgument(String key) {
        return arguments.get(key);
    }

    /** Returns {@code true} if the caller provided at least one argument. */
    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    @Override
    public String toString() {
        return "MCPToolsCall{id=" + getId() + ", name='" + name + "', argumentKeys=" + arguments.keySet() + "}";
    }
}
