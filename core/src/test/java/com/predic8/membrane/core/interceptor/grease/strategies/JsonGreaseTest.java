package com.predic8.membrane.core.interceptor.grease.strategies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

public class JsonGreaseTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    JsonGrease jsonGrease = new JsonGrease();
    String jsonString = """
            {"name":"John","age":30,"city":"New York"}""";
    Message msg = new Request();

    @Test
    void testJsonGrease() throws Exception {
        ObjectNode originalObject = (ObjectNode) objectMapper.readTree(jsonString);
        ObjectNode copyObject = originalObject.deepCopy();
        assertEquals(copyObject.toString(), originalObject.toString());
        JsonGrease.processJson(originalObject, JsonGrease::shuffleNodeFields);
        assertNotEquals(copyObject.toString(), originalObject.toString());
    }

    @Test
    void testNoGrease() {
        jsonGrease.setAddAdditionalFields(false);
        jsonGrease.setShuffleFields(false);
        msg.setBody(new Body(getJsonBytes()));
        msg.getHeader().setContentType(APPLICATION_JSON);
        assertEquals(jsonString, jsonGrease.apply(msg).getBody().toString());
    }

    @Test
    void testAdditionalFields() {
        jsonGrease.setAddAdditionalFields(true);
        jsonGrease.setShuffleFields(false);
        msg.setBody(new Body(getJsonBytes()));
        msg.getHeader().setContentType(APPLICATION_JSON);
        assertTrue(jsonGrease.apply(msg).getBody().toString().contains("\"grease\":\"Field added by Membrane's Grease plugin\""));
    }


    @Test
    void testShuffle() {
        jsonGrease.setShuffleFields(true);
        jsonGrease.setAddAdditionalFields(false);
        msg.setBody(new Body(getJsonBytes()));
        msg.getHeader().setContentType(APPLICATION_JSON);
        assertNotEquals(jsonString, jsonGrease.apply(msg).getBody().toString());
    }

    @Test
    void testBoth() {
        jsonGrease.setAddAdditionalFields(true);
        jsonGrease.setShuffleFields(true);
        msg.setBody(new Body(getJsonBytes()));
        msg.getHeader().setContentType(APPLICATION_JSON);
        Message applied = jsonGrease.apply(msg);
        assertNotEquals(jsonString, applied.getBodyAsStringDecoded());
        assertTrue(applied.toString().contains("\"grease\":\"Field added by Membrane's Grease plugin\""));
    }

    private byte @NotNull [] getJsonBytes() {
        return jsonString.getBytes(StandardCharsets.UTF_8);
    }
}