package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import java.math.*;
import java.util.*;

public class JsonUtil {

    public static final ObjectMapper om = new ObjectMapper();

    public static JsonNode mapToJson(Object m) {
        return om.convertValue(m, JsonNode.class);
    }

    public static JsonNode getNumbers(String name, BigDecimal n) {
        ObjectNode root = om.createObjectNode();
        root.put(name,n);
        return root;
    }

    public static JsonNode getStrings(String name, String value) {
        ObjectNode root = om.createObjectNode();
        root.put(name,value);
        return root;
    }
}
