package com.predic8.membrane.annot.yaml;

import com.fasterxml.jackson.databind.JsonNode;

public class ParsingException extends RuntimeException {
    private final JsonNode node;

    public ParsingException(String message, JsonNode node) {
        super(message);
        this.node = node;
    }

    public ParsingException(Throwable cause, JsonNode node) {
        super(cause);
        this.node = node;
    }

    public JsonNode getNode() {
        return node;
    }
}
