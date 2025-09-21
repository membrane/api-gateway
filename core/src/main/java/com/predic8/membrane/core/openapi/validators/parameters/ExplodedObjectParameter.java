package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;

public class ExplodedObjectParameter extends AbstractParameter {
    @Override
    public JsonNode getJson() throws JsonProcessingException {
        ObjectNode obj = FACTORY.objectNode();
        getSchema(api, parameter).getProperties().forEach((key, value) -> {
            obj.put(key, values.get(key).getFirst());
        });
        return obj;
    }
}
