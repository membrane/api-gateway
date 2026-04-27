package com.predic8.membrane.core.jsonrpc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ResponseKind.ERROR;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ResponseKind.SUCCESS;

/**
 * Represents a JSON-RPC 2.0 response as defined by https://www.jsonrpc.org/specification.
 *
 * <p>A response is either a <em>success</em> or an <em>error</em> — the two are
 * mutually exclusive:</p>
 * <ul>
 *   <li>Success: {@code result} is present, {@code error} is absent.</li>
 *   <li>Error:   {@code error} is present, {@code result} is absent.</li>
 * </ul>
 *
 * <p>Wire format (success):</p>
 * <pre>{@code
 * { "jsonrpc": "2.0", "id": 1, "result": { ... } }
 * }</pre>
 *
 * <p>Wire format (error):</p>
 * <pre>{@code
 * { "jsonrpc": "2.0", "id": 1, "error": { "code": -32600, "message": "...", "data": ... } }
 * }</pre>
 *
 * <p>The {@code id} member mirrors the {@code id} of the originating request.
 * It MUST be {@code null} if the id could not be determined (e.g. parse error).</p>
 *
 * <p>Use the static factories {@link #success(Object, Object)} and
 * {@link #error(Object, int, String)} (or {@link #error(Object, int, String, Object)})
 * to construct instances. Use {@link #from(JSONRPCRequest, Object)} to build a
 * success response that echoes the id of an existing {@link JSONRPCRequest}.</p>
 *
 * <p>Standard error codes are provided as constants, e.g. {@link #ERR_PARSE_ERROR}.</p>
 */
@JsonPropertyOrder({"jsonrpc", "id", "result", "error"})
@JsonSerialize(using = JSONRPCResponse.Serializer.class)
public class JSONRPCResponse {

    /** JSON-RPC protocol version — always {@code "2.0"}. */
    public static final String JSONRPC_VERSION = JSONRPCRequest.JSONRPC_VERSION;

    // ---------- Standard error codes (JSON-RPC 2.0 spec, section 5.1) ----------

    /** Invalid JSON was received by the server. */
    public static final int ERR_PARSE_ERROR      = -32700;
    /** The JSON sent is not a valid Request object. */
    public static final int ERR_INVALID_REQUEST  = -32600;
    /** The method does not exist / is not available. */
    public static final int ERR_METHOD_NOT_FOUND = -32601;
    /** Invalid method parameter(s). */
    public static final int ERR_INVALID_PARAMS   = -32602;
    /** Internal JSON-RPC error. */
    public static final int ERR_INTERNAL_ERROR   = -32603;

    private static final ObjectMapper OM = new ObjectMapper();

    @JsonProperty("jsonrpc")
    private String jsonrpc = JSONRPC_VERSION;

    /** May be String, Number, or null. Mirrors the id of the originating request. */
    @JsonProperty("id")
    private Object id;

    /**
     * Present on success responses; absent (null) on error responses.
     */
    @JsonProperty("result")
    private Object result;

    /** Present on error responses; absent (null) on success responses. */
    @JsonProperty("error")
    private Error error;

    /** Distinguishes between a success response carrying {@code result} and an error response carrying {@code error}. */
    @JsonIgnore
    private ResponseKind responseKind;

    // ---------- Constructors ----------

    /** No-arg constructor for Jackson deserialization. */
    public JSONRPCResponse() {}

    private JSONRPCResponse(Object id, Object result, Error error, ResponseKind responseKind) {
        this.id = id;
        this.result = result;
        this.error = error;
        this.responseKind = responseKind;
    }

    // ---------- Static factories ----------

    /**
     * Creates a success response with the given {@code id} and {@code result}.
     *
     * @param id     the id echoed from the request (String, Number, or null)
     * @param result the result value (any JSON-serialisable object, including null)
     */
    public static JSONRPCResponse success(Object id, Object result) {
        return new JSONRPCResponse(id, result, null, SUCCESS);
    }

    /**
     * Creates a success response that echoes the id of the originating {@link JSONRPCRequest}.
     *
     * @param request the request to reply to
     * @param result  the result value
     */
    public static JSONRPCResponse from(JSONRPCRequest request, Object result) {
        Objects.requireNonNull(request, "request must not be null");
        return new JSONRPCResponse(request.getId(), result, null, SUCCESS);
    }

    /**
     * Creates an error response with the given {@code id}, error {@code code}, and {@code message}.
     *
     * @param id      the id echoed from the request, or null if the id could not be determined
     * @param code    the JSON-RPC error code (see {@code ERR_*} constants)
     * @param message a human-readable error description
     */
    public static JSONRPCResponse error(Object id, int code, String message) {
        return new JSONRPCResponse(id, null, new Error(code, message, null), ERROR);
    }

    /**
     * Creates an error response with optional additional {@code data}.
     *
     * @param id      the id echoed from the request, or null if the id could not be determined
     * @param code    the JSON-RPC error code (see {@code ERR_*} constants)
     * @param message a human-readable error description
     * @param data    optional additional error information (any JSON-serialisable object)
     */
    public static JSONRPCResponse error(Object id, int code, String message, Object data) {
        return new JSONRPCResponse(id, null, new Error(code, message, data), ERROR);
    }

    /**
     * Creates an error response that echoes the id of the originating {@link JSONRPCRequest}.
     *
     * @param request the request to reply to
     * @param code    the JSON-RPC error code (see {@code ERR_*} constants)
     * @param message a human-readable error description
     */
    public static JSONRPCResponse errorFor(JSONRPCRequest request, int code, String message) {
        Objects.requireNonNull(request, "request must not be null");
        return new JSONRPCResponse(request.getId(), null, new Error(code, message, null), ERROR);
    }

    // ---------- Parsing ----------

    /**
     * Parses a JSON-RPC 2.0 response from the given {@link InputStream}.
     *
     * @throws IOException if the JSON is malformed or violates the JSON-RPC 2.0 structure
     */
    public static JSONRPCResponse parse(InputStream is) throws IOException {
        Objects.requireNonNull(is, "input stream must not be null");
        return fromNode(OM.readTree(is));
    }

    /**
     * Parses a JSON-RPC 2.0 response from the given JSON string.
     *
     * @throws IOException if the JSON is malformed or violates the JSON-RPC 2.0 structure
     */
    public static JSONRPCResponse parse(String json) throws IOException {
        Objects.requireNonNull(json, "json must not be null");
        return fromNode(OM.readTree(json));
    }

    private static JSONRPCResponse fromNode(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Invalid JSON-RPC response: expected JSON object");
        }

        JSONRPCResponse resp = new JSONRPCResponse();

        JsonNode versionNode = root.get("jsonrpc");
        resp.jsonrpc = versionNode != null && !versionNode.isNull() ? versionNode.asText() : null;
        if (!JSONRPC_VERSION.equals(resp.jsonrpc)) {
            throw new IOException("Unsupported or missing jsonrpc version: " + resp.jsonrpc);
        }

        JsonNode idNode = root.get("id");
        if (idNode != null && !idNode.isNull()) {
            if (idNode.isTextual()) {
                resp.id = idNode.asText();
            } else if (idNode.isIntegralNumber()) {
                resp.id = idNode.longValue();
            } else if (idNode.isNumber()) {
                resp.id = idNode.numberValue();
            } else {
                throw new IOException("Invalid JSON-RPC response: 'id' must be string, number, or null");
            }
        }

        boolean hasResult = root.has("result");
        boolean hasError  = root.has("error");

        if (hasResult && hasError) {
            throw new IOException("Invalid JSON-RPC response: 'result' and 'error' are mutually exclusive");
        }
        if (!hasResult && !hasError) {
            throw new IOException("Invalid JSON-RPC response: either 'result' or 'error' must be present");
        }

        if (hasResult) {
            JsonNode resultNode = root.get("result");
            resp.setResult(resultNode.isNull() ? null : OM.convertValue(resultNode, Object.class));
        } else {
            JsonNode errorNode = root.get("error");
            if (!errorNode.isObject()) {
                throw new IOException("Invalid JSON-RPC response: 'error' must be a JSON object");
            }
            resp.setError(Error.fromNode(errorNode));
        }

        return resp;
    }

    // ---------- Serialization ----------

    public String toJson() throws IOException {
        return OM.writeValueAsString(this);
    }

    public void writeTo(OutputStream os) throws IOException {
        OM.writeValue(os, this);
    }

    // ---------- Convenience ----------

    /** Returns {@code true} if this is a success response (no {@code error} member). */
    @JsonIgnore
    public boolean isSuccess() {
        return getResponseKind() == SUCCESS;
    }

    /** Returns {@code true} if this is an error response. */
    @JsonIgnore
    public boolean isError() {
        return getResponseKind() == ERROR;
    }

    @JsonIgnore
    public ResponseKind getResponseKind() {
        if (responseKind != null) {
            return responseKind;
        }
        return error != null ? ERROR : SUCCESS;
    }

    /** Convenience accessor — returns the id as String, or null. */
    @JsonIgnore
    public String getIdAsString() {
        return id == null ? null : id.toString();
    }

    // ---------- Getters / setters ----------

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.error = null;
        this.responseKind = SUCCESS;
    }

    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
        this.result = null;
        this.responseKind = ERROR;
    }

    // ---------- Object overrides ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSONRPCResponse that)) return false;
        return Objects.equals(jsonrpc, that.jsonrpc)
                && Objects.equals(id, that.id)
                && Objects.equals(result, that.result)
                && Objects.equals(error, that.error)
                && getResponseKind() == that.getResponseKind();
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonrpc, id, result, error, getResponseKind());
    }

    @Override
    public String toString() {
        return "JSONRPCResponse{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", id=" + id +
                (error == null ? ", result=" + result : ", error=" + error) +
                '}';
    }

    public enum ResponseKind {
        SUCCESS,
        ERROR
    }

    static final class Serializer extends StdSerializer<JSONRPCResponse> {

        Serializer() {
            super(JSONRPCResponse.class);
        }

        @Override
        public void serialize(JSONRPCResponse value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("jsonrpc", value.jsonrpc);

            if (value.id == null) {
                gen.writeNullField("id");
            } else {
                gen.writeFieldName("id");
                provider.defaultSerializeValue(value.id, gen);
            }

            if (value.getResponseKind() == SUCCESS) {
                gen.writeFieldName("result");
                provider.defaultSerializeValue(value.result, gen);
            } else {
                gen.writeFieldName("error");
                provider.defaultSerializeValue(value.error, gen);
            }

            gen.writeEndObject();
        }
    }

    // ---------- Nested type: Error ----------

    /**
     * Represents the JSON-RPC 2.0 {@code error} member.
     *
     * <p>Wire format:</p>
     * <pre>{@code
     * { "code": -32600, "message": "Invalid Request", "data": { ... } }
     * }</pre>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"code", "message", "data"})
    public static final class Error {

        @JsonProperty("code")
        private int code;

        @JsonProperty("message")
        private String message;

        /** Optional additional information about the error (any JSON-serialisable object). */
        @JsonProperty("data")
        private Object data;

        /** No-arg constructor for Jackson deserialization. */
        public Error() {}

        public Error(int code, String message) {
            this.code = code;
            this.message = Objects.requireNonNull(message, "message must not be null");
        }

        public Error(int code, String message, Object data) {
            this.code = code;
            this.message = Objects.requireNonNull(message, "message must not be null");
            this.data = data;
        }

        private static Error fromNode(JsonNode node) throws IOException {
            JsonNode codeNode = node.get("code");
            if (codeNode == null || !codeNode.isIntegralNumber()) {
                throw new IOException("Invalid JSON-RPC error: 'code' must be an integer");
            }
            JsonNode messageNode = node.get("message");
            if (messageNode == null || !messageNode.isTextual()) {
                throw new IOException("Invalid JSON-RPC error: 'message' must be a string");
            }
            Object data = null;
            JsonNode dataNode = node.get("data");
            if (dataNode != null && !dataNode.isNull()) {
                data = new ObjectMapper().convertValue(dataNode, Object.class);
            }
            return new Error(codeNode.intValue(), messageNode.asText(), data);
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Error that)) return false;
            return code == that.code
                    && Objects.equals(message, that.message)
                    && Objects.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, message, data);
        }

        @Override
        public String toString() {
            return "Error{code=" + code + ", message='" + message + "'" +
                    (data != null ? ", data=" + data : "") + "}";
        }
    }
}
