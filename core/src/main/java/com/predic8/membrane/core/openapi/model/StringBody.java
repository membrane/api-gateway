package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;

import java.io.*;

public class StringBody implements Body {

    private String payload;

    public StringBody(String s) {
        payload=s;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String asString() {
        return payload;
    }

    @Override
    public JsonNode getJson() throws IOException {
        return om.readValue(payload.getBytes(), JsonNode.class);
    }
}
