package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;

public class NoBody implements Body {
    @Override
    public String asString() {
        return "";
    }

    @Override
    public JsonNode getJson() {
        throw new RuntimeException("NoBody does not have JSON content!");
    }
}
