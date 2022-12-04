package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;

import java.io.*;

public class JsonBody implements Body {

    private JsonNode payload;

    public JsonBody(JsonNode s) {
        payload=s;
    }

    public JsonNode getPayload() {
        return payload;
    }

    @Override
    public String asString() {
        return payload.toString();
    }

    @Override
    public JsonNode getJson() throws IOException {
        return payload;
    }
}
