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
 * Typed view of an MCP {@code tools/list} request.
 *
 * <p>The client sends this request to retrieve all tools the server exposes.
 * The response is a {@link MCPToolsListResponse}.</p>
 *
 * <p>Wire format:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id":      1,
 *   "method":  "tools/list",
 *   "params":  { "cursor": "optional-pagination-cursor" }
 * }
 * }</pre>
 *
 * <p>{@code params} is optional. If present, {@code cursor} is an opaque string
 * returned by a previous {@code tools/list} response as {@code nextCursor} and
 * signals that the client wants the next page of results.</p>
 */
public class MCPToolsList extends MCPRequest {

    public static final String METHOD = "tools/list";

    /**
     * Opaque pagination cursor, or {@code null} if the client requests the first page.
     * Echoed from {@code params.cursor} of the incoming request.
     */
    private final String cursor;

    public MCPToolsList(JSONRPCRequest request) {
        super(request, METHOD);
        this.cursor = extractCursor(request);
    }

    /** Static factory equivalent to {@link #MCPToolsList(JSONRPCRequest)}. */
    public static MCPToolsList from(JSONRPCRequest request) {
        return new MCPToolsList(request);
    }

    // ---------- Helpers ----------

    private static String extractCursor(JSONRPCRequest request) {
        if (!request.hasNamedParams()) return null;
        Object cursor = request.getParamsMap().get("cursor");
        return cursor instanceof String s ? s : null;
    }

    // ---------- Accessors ----------

    /** Returns the pagination cursor, or {@code null} for the first page. */
    public String getCursor() {
        return cursor;
    }

    /** Returns {@code true} if this request is for a continuation page. */
    public boolean hasCursor() {
        return cursor != null;
    }

    @Override
    public String toString() {
        return "MCPToolsList{id=" + getId() + ", cursor=" + cursor + "}";
    }
}
