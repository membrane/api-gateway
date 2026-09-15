/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.util.CompilerResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonSchemaGeneratorTest {

    private static final String SCHEMA_RESOURCE = "com/predic8/membrane/demo/config/json/membrane.schema.json";

    /**
     * Boxed attribute types have to yield the same schema type as their primitive counterparts,
     * instead of falling through to the bean reference branch and becoming strings.
     */
    @Test
    void boxedAttributesAreScalars() throws Exception {
        var result = compile(splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                    @MCAttribute
                    public void setBoxedInt(Integer value) {}
                    public Integer getBoxedInt() { return null; }
                
                    @MCAttribute
                    public void setPrimitiveInt(int value) {}
                    public int getPrimitiveInt() { return 0; }
                
                    @MCAttribute
                    public void setBoxedLong(Long value) {}
                    public Long getBoxedLong() { return null; }
                
                    @MCAttribute
                    public void setBoxedDouble(Double value) {}
                    public Double getBoxedDouble() { return null; }
                
                    @MCAttribute
                    public void setBoxedBoolean(Boolean value) {}
                    public Boolean getBoxedBoolean() { return null; }
                
                    @MCAttribute
                    public void setText(String value) {}
                    public String getText() { return null; }
                }
                """), false);
        assertCompilerResult(true, result);

        JsonNode properties = schema(result)
                .at("/$defs/com_predic8_membrane_demo_config_spring_DemoParser/properties");

        assertEquals("[\"integer\",\"string\"]", properties.at("/primitiveInt/type").toString());
        assertEquals(properties.at("/primitiveInt/type"), properties.at("/boxedInt/type"));
        assertEquals(properties.at("/primitiveInt/type"), properties.at("/boxedLong/type"));
        assertEquals("[\"number\",\"string\"]", properties.at("/boxedDouble/type").toString());
        assertEquals("[\"boolean\",\"string\"]", properties.at("/boxedBoolean/type").toString());
        assertEquals("\"string\"", properties.at("/text/type").toString());
    }

    @Test
    void enumListsExposeIndividualChoices() throws Exception {
        var result = compile(splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.List;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                    public enum Algorithm { AES128_CBC, RSA_1_5 }
                    @MCChildElement
                    public void setAlgorithms(List<Algorithm> values) {}
                    public List<Algorithm> getAlgorithms() { return List.of(); }
                }
                """), false);
        assertCompilerResult(true, result);
        JsonNode algorithms = schema(result)
                .at("/$defs/com_predic8_membrane_demo_config_spring_DemoParser/properties/algorithms");
        assertEquals("array", algorithms.path("type").asText());
        assertEquals("string", algorithms.at("/items/type").asText());
        assertEquals("[\"aes128_cbc\",\"rsa_1_5\"]", algorithms.at("/items/enum").toString());
    }

    private static JsonNode schema(CompilerResult result) throws IOException {
        try (InputStream is = result.classLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            assertNotNull(is, "membrane.schema.json was not generated");
            return new ObjectMapper().readTree(is);
        }
    }
}
