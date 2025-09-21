package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

import static com.predic8.membrane.core.util.JsonUtil.*;

/**
 * TODO implement for objects in parameters
 */
public class ObjectParameter extends AbstractParameter{

    @Override
    public JsonNode getJson() throws JsonProcessingException {
        Deque<String> tokens = new ArrayDeque<>();
        Collections.addAll(tokens, getValuesForParameter().getFirst().split(","));
        if (tokens.size() == 0) {
            return FACTORY.objectNode();
        }
        if (tokens.size() == 1) {
            if ("null".equals(tokens.getFirst())) {
                return null;
            }
            // foo= => foo: "" => Let assume an empty parameter is an empty array
            if (tokens.getFirst().isEmpty()) {
                return FACTORY.objectNode();
            }
        }
        ObjectNode obj = FACTORY.objectNode();
        while (tokens.size() > 0) {
            String fieldName = tokens.pollFirst();
            String valueString = tokens.pollFirst();
            obj.put(fieldName, scalarAsJson(valueString));
        }
        return obj;
    }
}
