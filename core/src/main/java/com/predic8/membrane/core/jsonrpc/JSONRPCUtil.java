package com.predic8.membrane.core.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigInteger;

import static com.predic8.membrane.core.jsonrpc.JSONRPCRequest.JSONRPC_VERSION;

final class JSONRPCUtil {

    private JSONRPCUtil() {
    }

    static String parseVersion(JsonNode root, String messageType) throws IOException {
        JsonNode versionNode = root.get("jsonrpc");
        String version = versionNode != null && !versionNode.isNull() ? versionNode.asText() : null;
        if (!JSONRPC_VERSION.equals(version)) {
            throw new IOException("Unsupported or missing jsonrpc version in " + messageType + ": " + version);
        }
        return version;
    }

    static void validateVersion(String jsonrpc) {
        if (!JSONRPC_VERSION.equals(jsonrpc)) {
            throw new IllegalArgumentException("jsonrpc must be '" + JSONRPC_VERSION + "'");
        }
    }

    static String normalizeMethod(String method) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        return method;
    }

    static Object parseId(JsonNode idNode, String messageType) throws IOException {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.isTextual()) {
            return idNode.asText();
        }
        if (idNode.isIntegralNumber() && idNode.canConvertToLong()) {
            return idNode.longValue();
        }
        throw new IOException("Invalid JSON-RPC " + messageType + ": 'id' must be string, integer, or null");
    }

    static Object normalizeId(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof String) {
            return id;
        }
        if (id instanceof Byte || id instanceof Short || id instanceof Integer || id instanceof Long) {
            return ((Number) id).longValue();
        }
        if (id instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("id is out of long range", e);
            }
        }
        throw new IllegalArgumentException("id must be String, Integer, or null");
    }
}
