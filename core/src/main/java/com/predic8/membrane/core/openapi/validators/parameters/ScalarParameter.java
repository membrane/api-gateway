package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

public class ScalarParameter extends AbstractParameter {

    @Override
    public JsonNode getJson() throws JsonProcessingException {
        if (values.isEmpty()) {
            // Interpret absence of a concrete value as JSON null; validator will enforce required/nullable.
            return FACTORY.nullNode();
        }
        return asJson(values.getFirst());
    }
}
