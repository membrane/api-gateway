package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Typed view of an MCP {@code initialize} request.
 *
 * <p>The MCP {@code initialize} method has the following params shape:</p>
 * <pre>{@code
 * {
 *   "protocolVersion": "2024-11-05",
 *   "capabilities":   { ... },
 *   "clientInfo":     { "name": "...", "version": "..." }
 * }
 * }</pre>
 *
 * <p>An instance is created from a {@link JSONRPCRequest} via {@link #MCPInitialize(JSONRPCRequest)}
 * or {@link #from(JSONRPCRequest)}. The constructor validates the JSON-RPC method name and
 * the structure of the {@code params} object.</p>
 */
public class MCPInitialize extends MCPRequest {

    public static final String METHOD = "initialize";

    private final String protocolVersion;
    private final Map<String, Object> capabilities;
    private final ClientInfo clientInfo;

    public MCPInitialize(JSONRPCRequest request) {
        super(request, METHOD);

        if (!request.hasNamedParams()) {
            throw new IllegalArgumentException(
                    "MCP 'initialize' requires a params object (named parameters)");
        }

        Map<String, Object> params = request.getParamsMap();
        this.protocolVersion = requireString(params, "protocolVersion");
        this.capabilities = asMap(params.get("capabilities"));
        this.clientInfo = ClientInfo.from(asMap(params.get("clientInfo")));
    }

    /** Static factory equivalent to {@link #MCPInitialize(JSONRPCRequest)}. */
    public static MCPInitialize from(JSONRPCRequest request) {
        return new MCPInitialize(request);
    }

    // ---------- Accessors ----------

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    // ---------- Helpers ----------

    private static String requireString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP 'initialize' params: '" + key + "' must be a non-empty string");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) return Collections.emptyMap();
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Expected JSON object, got: " + value.getClass().getSimpleName());
    }

    @Override
    public String toString() {
        return "MCPInitialize{" +
                "id=" + getId() +
                ", protocolVersion='" + protocolVersion + '\'' +
                ", capabilities=" + capabilities +
                ", clientInfo=" + clientInfo +
                '}';
    }

    // ---------- Nested types ----------

    /** Identification of the connecting MCP client. */
    public static final class ClientInfo {
        private final String name;
        private final String version;

        public ClientInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        static ClientInfo from(Map<String, Object> map) {
            if (map == null || map.isEmpty()) return new ClientInfo(null, null);
            Object name = map.get("name");
            Object version = map.get("version");
            return new ClientInfo(
                    name == null ? null : name.toString(),
                    version == null ? null : version.toString());
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClientInfo that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, version);
        }

        @Override
        public String toString() {
            return "ClientInfo{name='" + name + "', version='" + version + "'}";
        }
    }
}
