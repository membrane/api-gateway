package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;

public final class NodeValidationUtils {

    public static void ensureMappingStart(JsonNode node) throws ParsingException {
        if (!(node.isObject())) throw new ParsingException("Expected object", node);
    }

    public static void ensureSingleKey(JsonNode node) {
        ensureMappingStart(node);
        if (node.size() != 1) throw new ParsingException("Expected exactly one key.", node);
    }

    public static void ensureTextual(JsonNode node, String message) throws ParsingException {
        if (!node.isTextual()) throw new ParsingException(message, node);
    }

    public static void ensureArray(JsonNode node, String message) throws ParsingException {
        if (!node.isArray()) throw new ParsingException(message, node);
    }

    public static void ensureArray(JsonNode node) throws ParsingException {
        ensureArray(node, "Expected list.");
    }

}
