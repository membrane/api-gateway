package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.object;
import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.string;
import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaObject.*;

class SchemaObjectTest {

    ObjectMapper mapper = new ObjectMapper();

    JsonNodeFactory jnf = JsonNodeFactory.instance;

    @Test
    void toJson() throws Exception {
        var so = object("person")
                .property(string("name").required(true))
                .property(object("address")
                        .property(string("street"))
                        .property(string("city"))
                        .required(true))
                .property(new SchemaArray("tags")
                        .items(string("name"))
                );

        prettyPrint(so.json(jnf.objectNode()));
    }

    private void prettyPrint(JsonNode json) throws Exception {

        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        String prettyJson = writer.writeValueAsString(json);
        System.out.println(prettyJson);
    }

}