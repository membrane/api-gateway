package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;
import java.util.stream.*;

public class ArrayParameter extends AbstractParameter {

    @Override
    public JsonNode getJson() throws JsonProcessingException {
        ArrayNode an = factory.arrayNode();
        Stream<String> items = getItems();
        // e.g. foo=null
        if (items == null) {
            return factory.missingNode();
        }
        items.forEach(s -> an.add(asJson(s)));
        return an;
    }

    private Stream<String> getItems() {
        if (explode) {
            return values.stream();
        }
        String[] items = values.getFirst().split(",");
        if (items.length == 0) {
            return Stream.empty();
        }
        if (items.length == 1) {
            if (items[0].equals("null")) {
                return null;
            }
            // foo= => foo: "" => Let assume an empty parameter is an empty array
            if ("".equals(items[0])) {
                return Stream.empty();
            }
        }
        return Arrays.stream(items);
    }

}
