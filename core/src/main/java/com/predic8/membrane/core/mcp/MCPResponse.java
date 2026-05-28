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

import com.predic8.membrane.core.jsonrpc.JSONRPCResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Abstract generic base class for all MCP responses.
 *
 * <p>Wraps a {@link JSONRPCResponse} and delegates all JSON-RPC 2.0 envelope
 * concerns ({@code jsonrpc}, {@code id}, serialization) to it. Subclasses only
 * need to own their MCP-specific {@code Result} type {@code R}.</p>
 *
 * <p>Typical subclass:</p>
 * <pre>{@code
 * public class MCPToolsListResponse extends MCPResponse<MCPToolsListResponse.Result> {
 *
 *     public MCPToolsListResponse(MCPToolsList request) {
 *         super(request.getId(), new Result());
 *     }
 *
 *     public MCPToolsListResponse withTool(Tool tool) {
 *         getResult().getTools().add(tool);
 *         return this;
 *     }
 *
 *     // ... nested Result and Tool classes
 * }
 * }</pre>
 *
 * @param <R> the MCP-specific result type carried in the JSON-RPC {@code result} field
 *
 * @see MCPRequest      for the request side
 * @see JSONRPCResponse for the underlying envelope
 */
public abstract class MCPResponse<R> {

    /** The JSON-RPC 2.0 envelope — owns jsonrpc, id, and result serialization. */
    protected final JSONRPCResponse rpcResponse;

    /**
     * Creates a success response with the given {@code id} and initial {@code result}.
     *
     * @param id     the id to echo from the request (String, Number, or null)
     * @param result the MCP-specific result payload; must not be null
     */
    protected MCPResponse(Object id, R result) {
        Objects.requireNonNull(result, "result must not be null");
        this.rpcResponse = JSONRPCResponse.success(id, result);
    }

    // ---------- Serialization — delegates to JSONRPCResponse ----------

    /** Serializes the full JSON-RPC 2.0 envelope + MCP result to a JSON string. */
    public String toJson() throws IOException {
        return rpcResponse.toJson();
    }

    /** Writes the full JSON-RPC 2.0 envelope + MCP result to the given stream. */
    public void writeTo(OutputStream os) throws IOException {
        rpcResponse.writeTo(os);
    }

    /** Exposes the underlying {@link JSONRPCResponse} for lower-level access if needed. */
    public JSONRPCResponse toRpcResponse() {
        return rpcResponse;
    }

    // ---------- Accessors — delegate to JSONRPCResponse ----------

    public String getJsonrpc() {
        return rpcResponse.getJsonrpc();
    }

    public Object getId() {
        return rpcResponse.getId();
    }

    public void setId(Object id) {
        rpcResponse.setId(id);
    }

    @SuppressWarnings("unchecked")
    public R getResult() {
        return (R) rpcResponse.getResult();
    }

    public void setResult(R result) {
        rpcResponse.setResult(result);
    }
}
