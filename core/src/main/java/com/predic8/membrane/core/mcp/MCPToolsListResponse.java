package com.predic8.membrane.core.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Typed response for the MCP {@code tools/list} method.
 *
 * <p>Extends {@link MCPResponse} so that all JSON-RPC 2.0 envelope concerns
 * ({@code jsonrpc}, {@code id}, serialization) are handled by the base class.</p>
 *
 * <p>Wire format:</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "result": {
 *     "tools": [
 *       {
 *         "name":        "my_tool",
 *         "description": "Does something useful",
 *         "inputSchema": {
 *           "type": "object",
 *           "properties": { "param": { "type": "string" } },
 *           "required":   ["param"]
 *         }
 *       }
 *     ],
 *     "nextCursor": "optional-opaque-cursor"
 *   }
 * }
 * }</pre>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * MCPToolsListResponse resp = MCPToolsListResponse.from(toolsListRequest)
 *         .withTool(new MCPToolsListResponse.Tool(
 *                 "my_tool",
 *                 "Does something useful",
 *                 Map.of("type", "object",
 *                        "properties", Map.of("param", Map.of("type", "string")),
 *                        "required", List.of("param"))));
 * resp.writeTo(outputStream);
 * }</pre>
 */
public class MCPToolsListResponse extends MCPResponse<MCPToolsListResponse.Result> {

    // ---------- Constructors ----------

    /** Creates a response with no id and an empty {@link Result}. */
    public MCPToolsListResponse() {
        super(null, new Result());
    }

    /**
     * Creates a response with an explicit {@code id} and {@link Result}.
     *
     * @param id     the id to echo (String, Number, or null)
     * @param result the MCP result payload; must not be null
     */
    public MCPToolsListResponse(Object id, Result result) {
        super(id, result);
    }

    /**
     * Creates a response from an already-parsed {@link MCPToolsList} request.
     * Echoes the request's {@code id}.
     */
    public MCPToolsListResponse(MCPToolsList request) {
        super(request.getId(), new Result());
    }

    /**
     * Creates a response directly from a raw {@link JSONRPCRequest}.
     * Validates the method name and echoes the {@code id}.
     *
     * @throws IllegalArgumentException if the method is not {@code tools/list}
     */
    public MCPToolsListResponse(JSONRPCRequest request) {
        this(MCPToolsList.from(request));
    }

    /** Static factory equivalent to {@link #MCPToolsListResponse(JSONRPCRequest)}. */
    public static MCPToolsListResponse from(JSONRPCRequest request) {
        return new MCPToolsListResponse(request);
    }

    /** Static factory equivalent to {@link #MCPToolsListResponse(MCPToolsList)}. */
    public static MCPToolsListResponse from(MCPToolsList request) {
        return new MCPToolsListResponse(request);
    }

    // ---------- Builder-style helpers ----------

    /** Adds a single tool to the result. */
    public MCPToolsListResponse withTool(Tool tool) {
        requireNonNull(tool, "tool must not be null");
        getResult().tools.add(tool);
        return this;
    }

    /** Adds multiple tools to the result. */
    public MCPToolsListResponse withTools(List<Tool> tools) {
        requireNonNull(tools, "tools must not be null");
        getResult().tools.addAll(tools);
        return this;
    }

    /**
     * Sets the {@code nextCursor} for pagination.
     * Pass {@code null} (or omit) to indicate this is the last page.
     */
    public MCPToolsListResponse withNextCursor(String nextCursor) {
        getResult().setNextCursor(nextCursor);
        return this;
    }

    @Override
    public String toString() {
        return "MCPToolsListResponse{id=" + getId() + ", result=" + getResult() + "}";
    }

    // ---------- Nested types ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"tools", "nextCursor"})
    public static final class Result {

        @JsonProperty("tools")
        private List<Tool> tools = new ArrayList<>();

        /**
         * Opaque pagination cursor for the next page, or {@code null} if this is the last page.
         * The client passes this value back as {@code params.cursor} in the next {@code tools/list}.
         */
        @JsonProperty("nextCursor")
        private String nextCursor;

        public Result() {}

        public Result(List<Tool> tools, String nextCursor) {
            this.tools = tools != null ? tools : new ArrayList<>();
            this.nextCursor = nextCursor;
        }

        public List<Tool> getTools() { return tools; }
        public void setTools(List<Tool> tools) { this.tools = tools; }

        public String getNextCursor() { return nextCursor; }
        public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Result that)) return false;
            return Objects.equals(tools, that.tools)
                    && Objects.equals(nextCursor, that.nextCursor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tools, nextCursor);
        }

        @Override
        public String toString() {
            return "Result{tools=" + tools + ", nextCursor=" + nextCursor + "}";
        }
    }

    /**
     * Describes a single MCP tool.
     *
     * <p>The {@code inputSchema} is a JSON Schema object (type {@code "object"})
     * describing the tool's parameters. Clients use it to validate arguments before
     * calling {@code tools/call}.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "description", "inputSchema"})
    public static final class Tool {

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        /**
         * JSON Schema describing the tool's input parameters.
         * Must be an object schema, e.g.:
         * <pre>{@code
         * Map.of("type", "object",
         *        "properties", Map.of("query", Map.of("type", "string")),
         *        "required", List.of("query"))
         * }</pre>
         */
        @JsonProperty("inputSchema")
        private Map<String, Object> inputSchema;

        public Tool() {}

        /**
         * @param name        unique tool identifier (required)
         * @param description human-readable description shown to the LLM (optional)
         * @param inputSchema JSON Schema object for the tool's parameters (required)
         */
        public Tool(String name, String description, Map<String, Object> inputSchema) {
            this.name = requireNonNull(name, "name must not be null");
            this.description = description;
            this.inputSchema = requireNonNull(inputSchema, "inputSchema must not be null");
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = requireNonNull(name, "name must not be null"); }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Map<String, Object> getInputSchema() { return inputSchema; }
        public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = requireNonNull(inputSchema, "inputSchema must not be null"); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Tool that)) return false;
            return Objects.equals(name, that.name)
                    && Objects.equals(description, that.description)
                    && Objects.equals(inputSchema, that.inputSchema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, description, inputSchema);
        }

        @Override
        public String toString() {
            return "Tool{name='" + name + "', description='" + description + "'}";
        }
    }
}
