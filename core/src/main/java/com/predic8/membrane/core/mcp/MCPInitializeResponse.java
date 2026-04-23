package com.predic8.membrane.core.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed response for the MCP {@code initialize} method.
 *
 * <p>Extends {@link MCPResponse} so that all JSON-RPC 2.0 envelope concerns
 * ({@code jsonrpc}, {@code id}, serialization) are handled by the base class.</p>
 *
 * <p>Wire format:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id":      <same id as the request>,
 *   "result": {
 *     "protocolVersion": "2024-11-05",
 *     "capabilities":   { ... },
 *     "serverInfo":     { "name": "...", "version": "..." },
 *     "instructions":   "optional free-form text"
 *   }
 * }
 * }</pre>
 */
public class MCPInitializeResponse extends MCPResponse<MCPInitializeResponse.Result> {

    // ---------- Constructors ----------

    /** Creates a response with no id and an empty {@link Result}. */
    public MCPInitializeResponse() {
        super(null, new Result());
    }

    /**
     * Creates a response with an explicit {@code id} and {@link Result}.
     *
     * @param id     the id to echo (String, Number, or null)
     * @param result the MCP result payload; must not be null
     */
    public MCPInitializeResponse(Object id, Result result) {
        super(id, result);
    }

    /**
     * Creates a response from an already-parsed {@link MCPInitialize} request.
     * Echoes the request's {@code id} and pre-populates {@code protocolVersion}.
     */
    public MCPInitializeResponse(MCPInitialize request) {
        super(request.getId(), initialResult(request.getProtocolVersion()));
    }

    /**
     * Creates a response directly from a raw {@link JSONRPCRequest}.
     * Validates the method name and echoes the {@code id}.
     *
     * @throws IllegalArgumentException if the method is not {@code initialize} or params are missing
     */
    public MCPInitializeResponse(JSONRPCRequest request) {
        this(MCPInitialize.from(request));
    }

    /** Static factory equivalent to {@link #MCPInitializeResponse(JSONRPCRequest)}. */
    public static MCPInitializeResponse from(JSONRPCRequest request) {
        return new MCPInitializeResponse(request);
    }

    /** Static factory equivalent to {@link #MCPInitializeResponse(MCPInitialize)}. */
    public static MCPInitializeResponse from(MCPInitialize request) {
        return new MCPInitializeResponse(request);
    }

    private static Result initialResult(String protocolVersion) {
        Result r = new Result();
        r.setProtocolVersion(protocolVersion);
        return r;
    }

    // ---------- Builder-style helpers ----------

    public MCPInitializeResponse withProtocolVersion(String protocolVersion) {
        getResult().setProtocolVersion(protocolVersion);
        return this;
    }

    public MCPInitializeResponse withServerInfo(String name, String version) {
        getResult().setServerInfo(new ServerInfo(name, version));
        return this;
    }

    public MCPInitializeResponse withCapabilities(Map<String, Object> capabilities) {
        getResult().setCapabilities(capabilities);
        return this;
    }

    public MCPInitializeResponse withCapability(String key, Object value) {
        Result result = getResult();
        if (result.capabilities == null) {
            result.capabilities = new LinkedHashMap<>();
        }
        result.capabilities.put(key, value);
        return this;
    }

    public MCPInitializeResponse withInstructions(String instructions) {
        getResult().setInstructions(instructions);
        return this;
    }

    @Override
    public String toString() {
        return "MCPInitializeResponse{id=" + getId() + ", result=" + getResult() + "}";
    }

    // ---------- Nested types ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"protocolVersion", "capabilities", "serverInfo", "instructions"})
    public static final class Result {

        @JsonProperty("protocolVersion")
        private String protocolVersion;

        @JsonProperty("capabilities")
        private Map<String, Object> capabilities = new LinkedHashMap<>();

        @JsonProperty("serverInfo")
        private ServerInfo serverInfo;

        @JsonProperty("instructions")
        private String instructions;

        public Result() {}

        public Result(String protocolVersion, Map<String, Object> capabilities, ServerInfo serverInfo) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities != null ? capabilities : new LinkedHashMap<>();
            this.serverInfo = serverInfo;
        }

        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

        public Map<String, Object> getCapabilities() { return capabilities; }
        public void setCapabilities(Map<String, Object> capabilities) { this.capabilities = capabilities; }

        public ServerInfo getServerInfo() { return serverInfo; }
        public void setServerInfo(ServerInfo serverInfo) { this.serverInfo = serverInfo; }

        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Result that)) return false;
            return Objects.equals(protocolVersion, that.protocolVersion)
                    && Objects.equals(capabilities, that.capabilities)
                    && Objects.equals(serverInfo, that.serverInfo)
                    && Objects.equals(instructions, that.instructions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protocolVersion, capabilities, serverInfo, instructions);
        }

        @Override
        public String toString() {
            return "Result{protocolVersion='" + protocolVersion + "', capabilities=" + capabilities
                    + ", serverInfo=" + serverInfo + ", instructions='" + instructions + "'}";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "version"})
    public static final class ServerInfo {

        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        public ServerInfo() {}

        public ServerInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ServerInfo that)) return false;
            return Objects.equals(name, that.name) && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, version);
        }

        @Override
        public String toString() {
            return "ServerInfo{name='" + name + "', version='" + version + "'}";
        }
    }
}
