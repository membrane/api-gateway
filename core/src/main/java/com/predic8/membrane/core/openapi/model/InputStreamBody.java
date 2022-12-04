package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.Utils.inputStreamToString;

public class InputStreamBody implements Body {

    private final InputStream is;
    private JsonNode node;

    public InputStreamBody(InputStream is) {
        this.is = is;
    }

    public InputStream getInputStream() {
        return is;
    }

    @Override
    public String asString() throws IOException {
        return inputStreamToString(is);
    }

    @Override
    public JsonNode getJson() throws IOException {
        if (node != null)
            return node;

        node = om.readValue(is, JsonNode.class);
        return node;
    }


}
