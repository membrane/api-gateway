package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ReusableJsonGeneratorTest {

    @Test
    public void reusableJsonGenerator() throws IOException {

        String[] params = new String[]{
                "error", "invalid_request"
        };
        ReusableJsonGenerator jsonGen = new ReusableJsonGenerator();

        String json1 = generateJson(jsonGen, params);
        String json2 = generateJson(jsonGen, params);

        Assertions.assertEquals(json1, json2);
    }

    private String generateJson(ReusableJsonGenerator jsonGen, String[] params) throws IOException {
        String json = null;
        try (JsonGenerator gen = jsonGen.resetAndGet()) {
            gen.writeStartObject();
            for (int i = 0; i < params.length; i += 2)
                gen.writeObjectField(params[i], params[i + 1]);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }
        return json;
    }

}
