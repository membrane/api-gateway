package com.predic8.membrane.core.openapi.model;

import com.fasterxml.jackson.databind.*;

import java.io.*;

public interface Body {

    ObjectMapper om = new ObjectMapper();

    String asString() throws IOException;

    JsonNode getJson() throws IOException;
}
