package com.predic8.membrane.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a JSON-RPC 2.0 request as defined by https://www.jsonrpc.org/specification.
 *
 * <p>The {@code id} member may be a {@link String}, a {@link Number}, or {@code null}.
 * A request without an {@code id} is a <em>notification</em> and the server MUST NOT reply.</p>
 *
 * <p>The {@code params} member is structured: either a JSON Array (positional, see
 * {@link #getParamsList()}) or a JSON Object (named, see {@link #getParamsMap()}).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JSONRPCRequest {

    public static final String JSONRPC_VERSION = "2.0";

    private static final ObjectMapper OM = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};

    @JsonProperty("jsonrpc")
    private String jsonrpc = JSONRPC_VERSION;

    /** May be String, Number, or null. */
    private Object id;

    /** Distinguishes a missing {@code id} from an explicit {@code "id": null}. */
    @JsonIgnore
    private boolean idPresent;

    @JsonProperty("method")
    private String method;

    /** Populated when params is a JSON object; mutually exclusive with {@link #paramsList}. */
    @JsonIgnore
    private Map<String, Object> paramsMap;

    /** Populated when params is a JSON array; mutually exclusive with {@link #paramsMap}. */
    @JsonIgnore
    private List<Object> paramsList;

    public JSONRPCRequest() {}

    public JSONRPCRequest(Object id, String method, Map<String, Object> paramsMap) {
        this(id, true, method, paramsMap);
    }

    public JSONRPCRequest(Object id, String method, List<Object> paramsList) {
        this(id, true, method, paramsList);
    }

    public JSONRPCRequest(Object id, boolean idPresent, String method, Map<String, Object> paramsMap) {
        setId(id, idPresent);
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.paramsMap = paramsMap;
    }

    public JSONRPCRequest(Object id, boolean idPresent, String method, List<Object> paramsList) {
        setId(id, idPresent);
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.paramsList = paramsList;
    }

    // ---------- Parsing ----------

    public static JSONRPCRequest parse(InputStream is) throws IOException {
        Objects.requireNonNull(is, "input stream must not be null");
        return fromNode(OM.readTree(is));
    }

    public static JSONRPCRequest parse(String json) throws IOException {
        Objects.requireNonNull(json, "json must not be null");
        return fromNode(OM.readTree(json));
    }

    private static JSONRPCRequest fromNode(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Invalid JSON-RPC request: expected JSON object");
        }

        JSONRPCRequest req = new JSONRPCRequest();

        JsonNode versionNode = root.get("jsonrpc");
        req.jsonrpc = versionNode != null && !versionNode.isNull() ? versionNode.asText() : null;
        if (!JSONRPC_VERSION.equals(req.jsonrpc)) {
            throw new IOException("Unsupported or missing jsonrpc version: " + req.jsonrpc);
        }

        JsonNode methodNode = root.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            throw new IOException("Invalid JSON-RPC request: 'method' must be a string");
        }
        req.method = methodNode.asText();

        if (root.has("id")) {
            JsonNode idNode = root.get("id");
            if (idNode == null || idNode.isNull()) {
                req.setId(null, true);
            } else if (idNode.isTextual()) {
                req.setId(idNode.asText(), true);
            } else if (idNode.isIntegralNumber()) {
                req.setId(idNode.longValue(), true);
            } else if (idNode.isNumber()) {
                req.setId(idNode.numberValue(), true);
            } else {
                throw new IOException("Invalid JSON-RPC request: 'id' must be string, number, or null");
            }
        }

        JsonNode paramsNode = root.get("params");
        if (paramsNode != null && !paramsNode.isNull()) {
            if (paramsNode.isArray()) {
                req.paramsList = OM.convertValue(paramsNode, LIST_TYPE);
            } else if (paramsNode.isObject()) {
                req.paramsMap = OM.convertValue(paramsNode, MAP_TYPE);
            } else {
                throw new IOException("Invalid JSON-RPC request: 'params' must be array or object");
            }
        }

        return req;
    }

    // ---------- Serialization ----------

    /** Returns the params field for serialization (object, array, or null). */
    @JsonProperty("params")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getParams() {
        if (paramsMap != null) return paramsMap;
        return paramsList;
    }

    public String toJson() throws IOException {
        return OM.writeValueAsString(toNode());
    }

    public void writeTo(OutputStream os) throws IOException {
        OM.writeValue(os, toNode());
    }

    private ObjectNode toNode() {
        ObjectNode root = OM.createObjectNode();

        if (jsonrpc != null) {
            root.put("jsonrpc", jsonrpc);
        }
        if (idPresent) {
            if (id == null) {
                root.putNull("id");
            } else {
                root.set("id", OM.valueToTree(id));
            }
        }
        if (method != null) {
            root.put("method", method);
        }
        if (paramsMap != null) {
            root.set("params", OM.valueToTree(paramsMap));
        } else if (paramsList != null) {
            root.set("params", OM.valueToTree(paramsList));
        }

        return root;
    }

    // ---------- Convenience ----------

    @JsonIgnore
    public boolean isNotification() {
        return !idPresent;
    }

    @JsonIgnore
    public boolean hasNamedParams() {
        return paramsMap != null;
    }

    @JsonIgnore
    public boolean hasPositionalParams() {
        return paramsList != null;
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

    @JsonIgnore
    public boolean isIdPresent() {
        return idPresent;
    }

    /** Convenience accessor preserving backwards compatibility — returns the id as String, or null. */
    @JsonIgnore
    public String getIdAsString() {
        return id == null ? null : id.toString();
    }

    @JsonSetter("id")
    public void setId(Object id) {
        setId(id, true);
    }

    public void setId(Object id, boolean present) {
        this.id = present ? id : null;
        this.idPresent = present;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParamsMap() {
        return paramsMap;
    }

    public void setParamsMap(Map<String, Object> paramsMap) {
        this.paramsMap = paramsMap;
        if (paramsMap != null) this.paramsList = null;
    }

    public List<Object> getParamsList() {
        return paramsList;
    }

    public void setParamsList(List<Object> paramsList) {
        this.paramsList = paramsList;
        if (paramsList != null) this.paramsMap = null;
    }

    // ---------- Object overrides ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSONRPCRequest that)) return false;
        return Objects.equals(jsonrpc, that.jsonrpc)
                && idPresent == that.idPresent
                && Objects.equals(id, that.id)
                && Objects.equals(method, that.method)
                && Objects.equals(paramsMap, that.paramsMap)
                && Objects.equals(paramsList, that.paramsList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonrpc, id, idPresent, method, paramsMap, paramsList);
    }

    @Override
    public String toString() {
        return "JSONRPCRequest{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", id=" + (idPresent ? id : "<absent>") +
                ", method='" + method + '\'' +
                ", params=" + (paramsMap != null ? paramsMap : paramsList) +
                '}';
    }
}
