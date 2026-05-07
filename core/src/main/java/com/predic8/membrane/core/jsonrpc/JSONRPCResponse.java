package com.predic8.membrane.core.jsonrpc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ResponseKind.ERROR;
import static com.predic8.membrane.core.jsonrpc.JSONRPCResponse.ResponseKind.SUCCESS;
import static com.predic8.membrane.core.jsonrpc.JSONRPCUtil.*;
import static java.util.Objects.requireNonNull;

/**
 * Represents a JSON-RPC 2.0 response as defined by https://www.jsonrpc.org/specification.
 *
 * <p>A response is either a success carrying {@code result} or an error carrying
 * {@code error}. The {@code id} mirrors the originating request and is
 * {@code null} when the request id could not be determined.</p>
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
    private JSONRPCError error;

    @JsonIgnore
    private ResponseKind responseKind;

    public JSONRPCResponse() {}

    private JSONRPCResponse(Object id, Object result, JSONRPCError error, ResponseKind responseKind) {
        setId(id);
        if (responseKind == SUCCESS) {
            setResult(result);
            return;
        }
        setError(error);
    }

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
        return new JSONRPCResponse(requireResponseId(request), result, null, SUCCESS);
    }

    /**
     * Creates an error response with the given {@code id}, error {@code code}, and {@code message}.
     *
     * @param id      the id echoed from the request, or null if the id could not be determined
     * @param code    the JSON-RPC error code (see {@code ERR_*} constants)
     * @param message a human-readable error description
     */
    public static JSONRPCResponse error(Object id, int code, String message) {
        return new JSONRPCResponse(id, null, new JSONRPCError(code, message, null), ERROR);
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
        return new JSONRPCResponse(id, null, new JSONRPCError(code, message, data), ERROR);
    }


    /**
     * Parses a JSON-RPC 2.0 response from the given {@link InputStream}.
     *
     * @throws IOException if the JSON is malformed or violates the JSON-RPC 2.0 structure
     */
    public static JSONRPCResponse parse(InputStream is) throws IOException {
        requireNonNull(is, "input stream must not be null");
        return fromNode(OM.readTree(is));
    }

    /**
     * Parses a JSON-RPC 2.0 response from the given JSON string.
     *
     * @throws IOException if the JSON is malformed or violates the JSON-RPC 2.0 structure
     */
    public static JSONRPCResponse parse(String json) throws IOException {
        requireNonNull(json, "json must not be null");
        return fromNode(OM.readTree(json));
    }

    private static JSONRPCResponse fromNode(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Invalid JSON-RPC response: expected JSON object");
        }

        JSONRPCResponse resp = new JSONRPCResponse();

        resp.jsonrpc = parseVersion(root, "response");

        if (!root.has("id")) {
            throw new IOException("Invalid JSON-RPC response: 'id' is required");
        }

        JsonNode idNode = root.get("id");
        resp.setId(parseId(idNode, "response"));

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
            resp.setError(JSONRPCError.fromNode(errorNode));
        }

        return resp;
    }

    public String toJson() throws IOException {
        return OM.writeValueAsString(toNode());
    }

    public void writeTo(OutputStream os) throws IOException {
        OM.writeValue(os, toNode());
    }

    @JsonIgnore
    public boolean isSuccess() {
        return getResponseKind() == SUCCESS;
    }

    @JsonIgnore
    public boolean isError() {
        return getResponseKind() == ERROR;
    }

    @JsonIgnore
    public ResponseKind getResponseKind() {
        if (responseKind != null) {
            return responseKind;
        }
        if (error != null) {
            return ERROR;
        }
        throw new IllegalStateException("response kind is undefined until either result or error is set");
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        validateVersion(jsonrpc);
        this.jsonrpc = jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = normalizeId(id);
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
        this.error = null;
        this.responseKind = SUCCESS;
    }

    public void setError(JSONRPCError error) {
        this.error = requireNonNull(error, "error must not be null");
        this.result = null;
        this.responseKind = ERROR;
    }

    private ObjectNode toNode() {
        validate();

        ObjectNode root = OM.createObjectNode();
        root.put("jsonrpc", jsonrpc);

        if (id == null) {
            root.putNull("id");
        } else {
            root.set("id", OM.valueToTree(id));
        }

        if (responseKind == SUCCESS) {
            root.set("result", OM.valueToTree(result));
        } else {
            root.set("error", OM.valueToTree(error));
        }

        return root;
    }

    private void validate() {
        validateVersion(jsonrpc);
        id = normalizeId(id);

        ResponseKind kind = responseKind != null ? responseKind : (error != null ? ERROR : null);
        if (kind == null) {
            throw new IllegalStateException("response kind is undefined until either result or error is set");
        }
        if (kind == SUCCESS) {
            if (error != null) {
                throw new IllegalStateException("success response must not contain error");
            }
        } else {
            if (error == null) {
                throw new IllegalStateException("error response must contain error");
            }
            if (result != null) {
                throw new IllegalStateException("error response must not contain result");
            }
        }
    }

    private static Object requireResponseId(JSONRPCRequest request) {
        requireNonNull(request, "request must not be null");
        if (request.isNotification()) {
            throw new IllegalArgumentException("cannot create a JSON-RPC response for a notification");
        }
        return request.getId();
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
            provider.defaultSerializeValue(value.toNode(), gen);
        }
    }

    @JsonInclude(NON_NULL)
    @JsonPropertyOrder({"code", "message", "data"})
    public static class JSONRPCError {

        @JsonProperty("code")
        private int code;

        @JsonProperty("message")
        private String message;

        /** Optional additional information about the error (any JSON-serialisable object). */
        @JsonProperty("data")
        private Object data;

        public JSONRPCError(int code, String message, Object data) {
            this.code = code;
            setMessage(message);
            this.data = data;
        }

        private static JSONRPCError fromNode(JsonNode node) throws IOException {
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
                data = OM.convertValue(dataNode, Object.class);
            }
            return new JSONRPCError(codeNode.intValue(), messageNode.asText(), data);
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
            this.message = requireNonNull(message, "message must not be null");
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
            if (!(o instanceof JSONRPCError that)) return false;
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
            return "JSONRPCError{code=" + code + ", message='" + message + "'" +
                    (data != null ? ", data=" + data : "") + "}";
        }
    }

}
