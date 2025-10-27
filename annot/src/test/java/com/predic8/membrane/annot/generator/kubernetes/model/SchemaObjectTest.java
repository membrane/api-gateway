package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaObjectTest {

    ObjectMapper mapper = new ObjectMapper();

    JsonNodeFactory jnf = JsonNodeFactory.instance;

    @Test
    void toJson() {
        var so = object("person")
                .property(string("name").required(true))
                .property(object("address")
                        .property(string("street"))
                        .property(string("city"))
                        .required(true))
                .property(new SchemaArray("tags")
                        .items(string("name"))
                );

        ObjectNode json = so.json(jnf.objectNode());

        // prettyPrint(json);

        assertNotNull(json);
        assertTrue(json.get("properties").has("name"));
        assertTrue(json.get("properties").has("address"));
        assertTrue(json.get("properties").has("tags"));
    }

    private void prettyPrint(JsonNode json) throws Exception {

        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        String prettyJson = writer.writeValueAsString(json);
        System.out.println(prettyJson);
    }

}