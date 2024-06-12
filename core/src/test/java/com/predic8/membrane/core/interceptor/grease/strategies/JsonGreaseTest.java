package com.predic8.membrane.core.interceptor.grease.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Body;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class JsonGreaseTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    JsonGrease jsonGrease = new JsonGrease();
    String jsonString = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";

    @Test
    void testJsonGrease() throws Exception {
        ObjectNode originalObject = (ObjectNode) objectMapper.readTree(jsonString);
        ObjectNode copyObject = originalObject.deepCopy();
        assertEquals(copyObject.toString(), originalObject.toString());
        originalObject = JsonGrease.shuffleObject(originalObject);
        assertNotEquals(copyObject.toString(), originalObject.toString());
    }

    @Test
    void testNoGrease() {
        jsonGrease.setAddAdditionalFields(false);
        jsonGrease.setShuffleFields(false);
        Body body = new Body(jsonString.getBytes(StandardCharsets.UTF_8));
        assertEquals(body.toString() , jsonGrease.apply(body).toString());
    }

    @Test
    void testAdditionalFields() {
        jsonGrease.setAddAdditionalFields(true);
        jsonGrease.setShuffleFields(false);
        Body body = new Body(jsonString.getBytes(StandardCharsets.UTF_8));
        assertTrue(jsonGrease.apply(body).toString().contains("\"bar\":\"Field added by GreaseInterceptor\""));
    }

    @Test
    void testShuffle() {
        jsonGrease.setShuffleFields(true);
        jsonGrease.setAddAdditionalFields(false);
        Body body = new Body(jsonString.getBytes(StandardCharsets.UTF_8));
        assertNotEquals(body.toString(), jsonGrease.apply(body).toString());
    }

    @Test
    void testBoth() {
        jsonGrease.setAddAdditionalFields(true);
        jsonGrease.setShuffleFields(true);
        Body body = new Body(jsonString.getBytes(StandardCharsets.UTF_8));
        Body applied = jsonGrease.apply(body);
        assertNotEquals(body.toString(), applied.toString());
        assertTrue(applied.toString().contains("\"bar\":\"Field added by GreaseInterceptor\""));
    }
}