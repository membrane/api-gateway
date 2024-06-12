package com.predic8.membrane.core.interceptor.grease.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class JsonGreaseTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testJsonGrease() throws Exception {
        String jsonString = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";
        ObjectNode originalObject = (ObjectNode) objectMapper.readTree(jsonString);
        ObjectNode copyObject = originalObject.deepCopy();
        assertEquals(copyObject.toString(), originalObject.toString());
        originalObject = JsonGrease.shuffleObject(originalObject);
        assertNotEquals(copyObject.toString(), originalObject.toString());
    }
}