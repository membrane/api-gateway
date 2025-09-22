package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.databind.*;

import java.util.*;

public interface ParameterParser {
    JsonNode getJson() throws Exception;

    void setValues(Map<String, List<String>> values);
}
