package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

public class ScalarParameter extends AbstractParameter{
    @Override
    public JsonNode getJson() throws JsonProcessingException {
        return asJson(values.get(0));
    }
}
