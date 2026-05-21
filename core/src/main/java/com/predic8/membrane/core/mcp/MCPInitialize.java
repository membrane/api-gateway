/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.mcp;

import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

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
        this.capabilities = asMap(params, "capabilities");
        this.clientInfo = ClientInfo.from(asMap(params, "clientInfo"));
    }

    /** Static factory equivalent to {@link #MCPInitialize(JSONRPCRequest)}. */
    public static MCPInitialize from(JSONRPCRequest request) {
        return new MCPInitialize(request);
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }


    private static String requireString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP 'initialize' params: '" + key + "' must be a non-empty string");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("MCP 'initialize' params: '" + key + "' must be a JSON object");
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

    /** Identification of the connecting MCP client. */
    public static final class ClientInfo {
        private final String name;
        private final String version;

        public ClientInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        static ClientInfo from(Map<String, Object> map) {
            return new ClientInfo(requireString(map, "name"),requireString(map, "version"));
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
