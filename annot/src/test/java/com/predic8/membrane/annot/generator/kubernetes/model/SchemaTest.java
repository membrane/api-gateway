package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTest {

    JsonNodeFactory jnf = new JsonNodeFactory(false);

    @Test
    void test() {
        var schema = new Schema("foo").property(string("bar"));
        var json = schema.json(jnf.objectNode());

        assertNotNull(json);
        assertTrue(json.has("properties"));
        assertTrue(json.get("properties").has("bar"));

//        prettyPrint(json);
    }

    private static void prettyPrint(JsonNode json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object jsonObject = mapper.readValue(json.toString(), Object.class); // parse JSON
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        String prettyJson = writer.writeValueAsString(jsonObject);
        System.out.println(prettyJson);
    }

}