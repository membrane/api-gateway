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
