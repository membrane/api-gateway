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
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.annot.util.CompilerResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.MC_MAIN_DEMO;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * The YAML parser converts numbers and booleans in a {@code Map<String, String>} of other attributes to strings,
     * so the schema has to accept them, e.g. {@code callTimeout: 30000}.
     */
    @Test
    void stringOtherAttributesAcceptScalars() throws Exception {
        final var result = compile(splitSources(MC_MAIN_DEMO + """
                package com.predic8.membrane.demo;
                import com.predic8.membrane.annot.*;
                import java.util.Map;
                @MCElement(name="demo", topLevel=true, component=false)
                public class DemoElement {
                    @MCOtherAttributes
                    public void setProperties(Map<String, String> properties) {}
                    public Map<String, String> getProperties() { return Map.of(); }
                }
                """), false);
        assertCompilerResult(true, result);
        assertEquals("[\"string\",\"number\",\"boolean\"]", schema(result)
                .at("/$defs/com_predic8_membrane_demo_config_spring_DemoParser/additionalProperties/type").toString());
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

    @Test
    void requiredChildWithOneImplementationIsRequired() throws Exception {
        var schema = requiredChildrenSchema();
        assertEquals("[\"child\"]", schema.at("/$defs/com_predic8_membrane_demo_config_spring_SingleParser/required").toString());
        assertValid(schema, "single: { child: {} }");
        assertInvalid(schema, "single: {}");
    }

    @Test
    void requiredAbstractChildNeedsOneOfItsImplementations() throws Exception {
        var schema = requiredChildrenSchema();
        assertValid(schema, "cache: { memStore: {} }");
        assertValid(schema, "cache: { diskStore: {} }");
        assertInvalid(schema, "cache: {}");
    }

    @Test
    void everyRequiredChildMustBePresent() throws Exception {
        var schema = requiredChildrenSchema();
        assertValid(schema, "two: { child: {}, memStore: {} }");
        assertInvalid(schema, "two: { child: {} }");
        assertInvalid(schema, "two: { memStore: {} }");
    }

    @Test
    void refSatisfiesRequiredComponentChild() throws Exception {
        var schema = requiredChildrenSchema();
        assertValid(schema, "refCache: { sharedStore: {} }");
        assertValid(schema, "refCache: { $ref: '#/components/store' }");
        assertInvalid(schema, "refCache: {}");
    }

    @Test
    void flowItemIsOneObjectWithAPropertyPerPlugin() throws Exception {
        var items = flowSchema().at("/$defs/flowParser/items");
        assertEquals("object", items.path("type").asText());
        assertEquals(1, items.path("minProperties").asInt());
        assertEquals(1, items.path("maxProperties").asInt());
        assertFalse(items.path("additionalProperties").asBoolean(true));
        assertTrue(items.path("anyOf").isMissingNode());
        final var properties = new ArrayList<String>();
        items.path("properties").fieldNames().forEachRemaining(properties::add);
        assertEquals(Set.of("log", "cache", "if", "$ref"), Set.copyOf(properties));
    }

    @Test
    void validFlowItems() throws Exception {
        assertValid(flowSchema(), """
                api:
                  flow:
                    - log: { message: abc }
                    - cache: { store: {} }
                    - $ref: '#/components/log'
                    - if:
                        test: abc
                        flow:
                          - log: {}
                """);
    }

    @Test
    void invalidFlowItemReportsOnlyItsOwnError() throws Exception {
        final var schema = flowSchema();
        assertEquals(1, validate(schema, "api: { flow: [ { foo: {} } ] }").size());
        assertEquals(1, validate(schema, "api: { flow: [ {} ] }").size());
        assertEquals(1, validate(schema, "api: { flow: [ { log: {}, if: { test: abc } } ] }").size());
    }

    @Test
    void invalidPluginIsReportedInsideThePlugin() throws Exception {
        final var errors = validate(flowSchema(), "api: { flow: [ { cache: {} } ] }");
        assertFalse(errors.isEmpty());
        errors.forEach(e -> assertTrue(e.getInstanceLocation().toString().startsWith("/api/flow/0/cache"), e::toString));
    }

    private static final String FLOW_SOURCES = """
            package com.predic8.membrane.core.interceptor;
            public interface Interceptor {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import com.predic8.membrane.core.interceptor.Interceptor;
            import java.util.List;
            @MCElement(name="api", topLevel=true, component=false)
            public class ApiElement {
                @MCChildElement
                public void setFlow(List<Interceptor> flow) {}
                public List<Interceptor> getFlow() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import com.predic8.membrane.core.interceptor.Interceptor;
            @MCElement(name="log")
            public class LogElement implements Interceptor {
                @MCAttribute
                public void setMessage(String message) {}
                public String getMessage() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import com.predic8.membrane.core.interceptor.Interceptor;
            @MCElement(name="cache")
            public class CacheElement implements Interceptor {
                @Required
                @MCChildElement
                public void setStore(StoreElement store) {}
                public StoreElement getStore() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="store", component=false)
            public class StoreElement {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import com.predic8.membrane.core.interceptor.Interceptor;
            import java.util.List;
            @MCElement(name="if")
            public class IfElement implements Interceptor {
                @MCAttribute
                public void setTest(String test) {}
                public String getTest() { return null; }

                @MCChildElement
                public void setFlow(List<Interceptor> flow) {}
                public List<Interceptor> getFlow() { return null; }
            }
            """;

    private static JsonNode flowSchema() throws IOException {
        var result = compile(splitSources(MC_MAIN_DEMO + FLOW_SOURCES), false);
        assertCompilerResult(true, result);
        return schema(result);
    }

    private static final String REQUIRED_CHILDREN_SOURCES = """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="single", topLevel=true, component=false)
            public class SingleElement {
                @Required
                @MCChildElement
                public void setChild(ChildElement child) {}
                public ChildElement getChild() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="child", component=false)
            public class ChildElement {}
            ---
            package com.predic8.membrane.demo;
            public abstract class Store {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="memStore", component=false)
            public class MemStore extends Store {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="diskStore", component=false)
            public class DiskStore extends Store {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="cache", topLevel=true, component=false)
            public class CacheElement {
                @Required
                @MCChildElement
                public void setStore(Store store) {}
                public Store getStore() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="two", topLevel=true, component=false)
            public class TwoElement {
                @Required
                @MCChildElement(order=1)
                public void setChild(ChildElement child) {}
                public ChildElement getChild() { return null; }

                @Required
                @MCChildElement(order=2)
                public void setStore(Store store) {}
                public Store getStore() { return null; }
            }
            ---
            package com.predic8.membrane.demo;
            public abstract class SharedStore {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="sharedStore")
            public class SharedStoreElement extends SharedStore {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            @MCElement(name="refCache", topLevel=true, component=false)
            public class RefCacheElement {
                @Required
                @MCChildElement
                public void setStore(SharedStore store) {}
                public SharedStore getStore() { return null; }
            }
            """;

    private static JsonNode requiredChildrenSchema() throws IOException {
        var result = compile(splitSources(MC_MAIN_DEMO + REQUIRED_CHILDREN_SOURCES), false);
        assertCompilerResult(true, result);
        return schema(result);
    }

    private static void assertValid(JsonNode schema, String yaml) throws IOException {
        final var errors = validate(schema, yaml);
        assertTrue(errors.isEmpty(), () -> "Expected '%s' to be valid, but got %s".formatted(yaml, errors));
    }

    private static void assertInvalid(JsonNode schema, String yaml) throws IOException {
        assertFalse(validate(schema, yaml).isEmpty(), () -> "Expected '%s' to be invalid".formatted(yaml));
    }

    private static List<com.networknt.schema.Error> validate(JsonNode schema, String yaml) throws IOException {
        return SchemaRegistry.withDefaultDialect(DRAFT_2020_12)
                .getSchema(schema)
                .validate(new YAMLMapper().readTree(yaml));
    }

    private static JsonNode schema(CompilerResult result) throws IOException {
        try (InputStream is = result.classLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            assertNotNull(is, "membrane.schema.json was not generated");
            return new ObjectMapper().readTree(is);
        }
    }
}
