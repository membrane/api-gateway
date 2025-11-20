/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.generator.kubernetes.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.node.JsonNodeFactory;

import static com.predic8.membrane.annot.generator.kubernetes.model.SchemaFactory.string;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTest {

    JsonNodeFactory jnf = new JsonNodeFactory();

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