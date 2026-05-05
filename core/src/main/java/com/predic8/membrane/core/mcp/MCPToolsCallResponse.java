package com.predic8.membrane.core.mcp;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.jsonrpc.JSONRPCRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Typed response for the MCP {@code tools/call} method.
 *
 * <p>Wire format (success):</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 2,
 *   "result": {
 *     "content": [
 *       { "type": "text", "text": "Found 3 proxies: ..." }
 *     ],
 *     "isError": false
 *   }
 * }
 * }</pre>
 *
 * <p>Wire format (tool-level error — note: this is still a JSON-RPC <em>success</em>
 * response; the tool itself signals failure via {@code isError: true}):</p>
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 2,
 *   "result": {
 *     "content": [
 *       { "type": "text", "text": "Tool 'listProxies' failed: ..." }
 *     ],
 *     "isError": true
 *   }
 * }
 * }</pre>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * MCPToolsCallResponse.from(call)
 *         .withText("Found 3 proxies: api, soap, rest");
 *
 * // Tool-level error:
 * MCPToolsCallResponse.toolError(call, "Unknown tool: " + call.getName());
 * }</pre>
 */
public class MCPToolsCallResponse extends MCPResponse<MCPToolsCallResponse.Result> {

    private static final ObjectMapper OM = new ObjectMapper();

    // ---------- Constructors ----------

    public MCPToolsCallResponse() {
        super(null, new Result());
    }

    public MCPToolsCallResponse(Object id, Result result) {
        super(id, result);
    }

    public MCPToolsCallResponse(MCPToolsCall request) {
        super(request.getId(), new Result());
    }

    public MCPToolsCallResponse(JSONRPCRequest request) {
        this(MCPToolsCall.from(request));
    }

    // ---------- Static factories ----------

    public static MCPToolsCallResponse from(JSONRPCRequest request) {
        return new MCPToolsCallResponse(request);
    }

    public static MCPToolsCallResponse from(MCPToolsCall request) {
        return new MCPToolsCallResponse(request);
    }

    /**
     * Creates a tool-level error response ({@code isError: true}).
     * Note: this is still a valid JSON-RPC success response — the tool itself signals failure.
     */
    public static MCPToolsCallResponse toolError(MCPToolsCall request, String message) {
        return new MCPToolsCallResponse(request)
                .withText(message)
                .withIsError(true);
    }

    // ---------- Builder-style helpers ----------

    /** Appends a plain text content item. */
    public MCPToolsCallResponse withText(String text) {
        getResult().content.add(new TextContent(text));
        return this;
    }

    /** Appends a base64-encoded image content item. */
    public MCPToolsCallResponse withImage(String base64Data, String mimeType) {
        getResult().content.add(new ImageContent(base64Data, mimeType));
        return this;
    }

    /** Appends a resource content item. */
    public MCPToolsCallResponse withResource(String uri, String text) {
        getResult().content.add(new ResourceContent(uri, text));
        return this;
    }

    /**
     * Serializes {@code data} to a JSON string and appends it as a {@link TextContent} item.
     *
     * <p>This is the simplest way to return structured data — the LLM receives valid
     * JSON text it can reason about directly:</p>
     * <pre>{@code
     * .withJson(List.of(Map.of("name", "api", "port", 8080),
     *                   Map.of("name", "soap", "port", 8443)))
     * // → { "type": "text", "text": "[{\"name\":\"api\",\"port\":8080}, ...]" }
     * }</pre>
     *
     * @throws RuntimeException if {@code data} cannot be serialized to JSON
     */
    public MCPToolsCallResponse withJson(Object data) {
        try {
            getResult().content.add(new TextContent(OM.writeValueAsString(data)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize data to JSON", e);
        }
        return this;
    }

    /**
     * Serializes {@code data} to a JSON string and appends it as a {@link ResourceContent}
     * with {@code mimeType: "application/json"} and the given {@code uri}.
     *
     * <p>Prefer this over {@link #withJson(Object)} when the data represents a
     * named resource that clients may want to reference by URI:</p>
     * <pre>{@code
     * .withJsonResource("membrane://proxies", proxiesList)
     * // → { "type": "resource", "resource": { "uri": "membrane://proxies",
     * //       "mimeType": "application/json", "text": "[...]" } }
     * }</pre>
     *
     * @throws RuntimeException if {@code data} cannot be serialized to JSON
     */
    public MCPToolsCallResponse withJsonResource(String uri, Object data) {
        try {
            String json = OM.writeValueAsString(data);
            ResourceContent.Resource resource = new ResourceContent.Resource(uri, json);
            resource.setMimeType("application/json");
            getResult().content.add(new ResourceContent(resource));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize data to JSON", e);
        }
        return this;
    }

    /** Appends an arbitrary content item. */
    public MCPToolsCallResponse withContent(Content content) {
        Objects.requireNonNull(content, "content must not be null");
        getResult().content.add(content);
        return this;
    }

    /** Marks the response as a tool-level error. */
    public MCPToolsCallResponse withIsError(boolean isError) {
        getResult().setIsError(isError);
        return this;
    }

    @Override
    public String toString() {
        return "MCPToolsCallResponse{id=" + getId() + ", result=" + getResult() + "}";
    }

    // ---------- Nested types ----------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"content", "isError"})
    public static final class Result {

        @JsonProperty("content")
        private List<Content> content = new ArrayList<>();

        /** {@code true} if the tool itself encountered an error (distinct from a JSON-RPC error). */
        @JsonProperty("isError")
        private Boolean isError;

        public Result() {}

        public List<Content> getContent() { return content; }
        public void setContent(List<Content> content) { this.content = content; }

        public Boolean getIsError() { return isError; }
        public void setIsError(Boolean isError) { this.isError = isError; }

        @Override
        public String toString() {
            return "Result{content=" + content + ", isError=" + isError + "}";
        }
    }

    // ---------- Content types ----------

    /**
     * Base class for all MCP content items.
     * Jackson uses the {@code type} field to deserialize the correct subtype.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextContent.class,     name = "text"),
            @JsonSubTypes.Type(value = ImageContent.class,    name = "image"),
            @JsonSubTypes.Type(value = ResourceContent.class, name = "resource")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract static class Content {
        public abstract String getType();
    }

    /** A plain-text content item. */
    @JsonPropertyOrder({"type", "text"})
    public static final class TextContent extends Content {

        @JsonProperty("text")
        private String text;

        public TextContent() {}

        public TextContent(String text) {
            this.text = Objects.requireNonNull(text, "text must not be null");
        }

        @Override
        public String getType() { return "text"; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        @Override
        public String toString() { return "TextContent{text='" + text + "'}"; }
    }

    /** A base64-encoded image content item. */
    @JsonPropertyOrder({"type", "data", "mimeType"})
    public static final class ImageContent extends Content {

        @JsonProperty("data")
        private String data;

        @JsonProperty("mimeType")
        private String mimeType;

        public ImageContent() {}

        public ImageContent(String data, String mimeType) {
            this.data = Objects.requireNonNull(data, "data must not be null");
            this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        }

        @Override
        public String getType() { return "image"; }

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        @Override
        public String toString() { return "ImageContent{mimeType='" + mimeType + "'}"; }
    }

    /** A resource reference content item (URI + optional inline text or blob). */
    @JsonPropertyOrder({"type", "resource"})
    public static final class ResourceContent extends Content {

        @JsonProperty("resource")
        private Resource resource;

        public ResourceContent() {}

        public ResourceContent(String uri, String text) {
            this.resource = new Resource(uri, text);
        }

        public ResourceContent(Resource resource) {
            this.resource = Objects.requireNonNull(resource, "resource must not be null");
        }

        @Override
        public String getType() { return "resource"; }

        public Resource getResource() { return resource; }
        public void setResource(Resource resource) { this.resource = resource; }

        @Override
        public String toString() { return "ResourceContent{resource=" + resource + "}"; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonPropertyOrder({"uri", "mimeType", "text"})
        public static final class Resource {

            @JsonProperty("uri")
            private String uri;

            @JsonProperty("mimeType")
            private String mimeType;

            @JsonProperty("text")
            private String text;

            public Resource() {}

            public Resource(String uri, String text) {
                this.uri = Objects.requireNonNull(uri, "uri must not be null");
                this.text = text;
            }

            public String getUri() { return uri; }
            public void setUri(String uri) { this.uri = uri; }

            public String getMimeType() { return mimeType; }
            public void setMimeType(String mimeType) { this.mimeType = mimeType; }

            public String getText() { return text; }
            public void setText(String text) { this.text = text; }

            @Override
            public String toString() { return "Resource{uri='" + uri + "'}"; }
        }
    }
}
