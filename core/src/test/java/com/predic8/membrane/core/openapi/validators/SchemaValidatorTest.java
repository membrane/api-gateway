/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.node.BooleanNode.TRUE;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;
import static java.io.InputStream.nullInputStream;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaValidatorTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String ADDRESS_REF = "#/components/schemas/Address";

    static JsonSchemaValidator arrayValidator;
    static JsonSchemaValidator booleanValidator;
    static JsonSchemaValidator integerValidator;
    static JsonSchemaValidator numberValidator;
    static JsonSchemaValidator objectValidator;
    static JsonSchemaValidator stringValidator;

    @BeforeAll
    static void setUp() {
        arrayValidator = new ArrayValidator(null, null);
        booleanValidator = new BooleanValidator();
        integerValidator = new IntegerValidator();
        numberValidator = new NumberValidator();
        objectValidator = new ObjectValidator(null, null);
        stringValidator = new StringValidator(null);
    }

    @ParameterizedTest
    @MethodSource("validatorTestCases")
    void testCanValidate(JsonSchemaValidator validator, Object input, String expected) {
        if (input instanceof InputStream) {
            assertNull(validator.canValidate(input));
        } else {
            assertEquals(expected, validator.canValidate(input),"Input: " + input);
        }
    }

    private static Stream<Arguments> validatorTestCases() {
        JsonNode nonArrayNode = mapper.createObjectNode().put("key", "value");
        JsonNode stringNode = new TextNode("example");

        return Stream.of(
                // ArrayValidator test cases
                Arguments.of(arrayValidator, mapper.createArrayNode(), ARRAY),
                Arguments.of(arrayValidator, nonArrayNode, null),
                Arguments.of(arrayValidator, "notAnArray", null),
                Arguments.of(arrayValidator, null, null),

                // BooleanValidator test cases
                Arguments.of(booleanValidator, TRUE, BOOLEAN),
                Arguments.of(booleanValidator, "true", BOOLEAN),
                Arguments.of(booleanValidator, "false", BOOLEAN),
                Arguments.of(booleanValidator, "notABoolean", null),
                Arguments.of(booleanValidator, nonArrayNode, null),
                Arguments.of(booleanValidator, null, null),

                // IntegerValidator test cases
                Arguments.of(integerValidator, new IntNode(123), INTEGER),
                Arguments.of(integerValidator, "123", INTEGER),
                Arguments.of(integerValidator, "notAnInteger", null),
                Arguments.of(integerValidator, 123, INTEGER),
                Arguments.of(integerValidator, 123.45, null),
                Arguments.of(integerValidator, nonArrayNode, null),
                Arguments.of(integerValidator, null, null),

                // NumberValidator test cases
                Arguments.of(numberValidator, new TextNode("123.45"), null),
                Arguments.of(numberValidator, new TextNode("notANumber"), null),
                Arguments.of(numberValidator, "456.78", NUMBER),
                Arguments.of(numberValidator, "invalid", null),
                Arguments.of(numberValidator, 123, NUMBER),
                Arguments.of(numberValidator, 3.142, NUMBER), // Double
                Arguments.of(numberValidator, 382147189247.141592653589793, NUMBER), // Double
                Arguments.of(numberValidator, 10_000_000_000L, NUMBER), // Long
                Arguments.of(numberValidator, null, null),

                // ObjectValidator test cases
                Arguments.of(objectValidator, mapper.createObjectNode(), OBJECT),
                Arguments.of(objectValidator, stringNode, null),
                Arguments.of(objectValidator, nullInputStream(), null),

                // StringValidator test cases
                Arguments.of(stringValidator, stringNode, STRING),
                Arguments.of(stringValidator, "example", STRING),
                Arguments.of(stringValidator, mapper.createObjectNode().put(NUMBER, 123), null),
                Arguments.of(stringValidator, null, null),
                Arguments.of(stringValidator, 123, null)
        );
    }

    @Test
    void canValidateWithInputStream() {
        assertNull(objectValidator.canValidate(nullInputStream()));
    }

    @Nested
    class HasSiblings {

        @Test
        void refOnly() {
            assertFalse(SchemaValidator.hasSiblings(new Schema<>().$ref(ADDRESS_REF)));
        }

        @Test
        void emptySchema() {
            assertFalse(SchemaValidator.hasSiblings(new Schema<>()));
        }

        @Test
        void refAndType() {
            assertTrue(SchemaValidator.hasSiblings(new Schema<>().$ref(ADDRESS_REF).types(Set.of("string"))));
        }

        @Test
        void refAndRequired() {
            assertTrue(SchemaValidator.hasSiblings(new Schema<>().$ref(ADDRESS_REF).required(List.of("zip"))));
        }

        @Test
        void refAndReadOnly() {
            assertTrue(SchemaValidator.hasSiblings(new Schema<>().$ref(ADDRESS_REF).readOnly(true)));
        }

        /**
         * A different spec version alone is not a sibling keyword.
         */
        @Test
        void refOnlyWithSpecVersion() {
            Schema<?> schema = new Schema<>().$ref(ADDRESS_REF);
            schema.setSpecVersion(SpecVersion.V31);
            assertFalse(SchemaValidator.hasSiblings(schema));
        }

        @Test
        void keywordWithoutRef() {
            assertTrue(SchemaValidator.hasSiblings(new Schema<>().types(Set.of("string"))));
        }

        /**
         * The hand built schemas above have to agree with what the parser produces. The parser uses
         * Schema subclasses, so a plain $ref must not be mistaken for a schema with siblings.
         */
        @ParameterizedTest
        @CsvSource({"nullableRef,true", "readOnlyRef,true", "extraRequiredRef,true",
                    "narrowedRef,true", "plainRef,false"})
        void parsedSchema(String property, boolean expected) {
            OpenAPI api = parseOpenAPI(getClass().getResourceAsStream("/openapi/specs/oas31/ref-siblings.yaml"));
            Schema<?> schema = (Schema<?>) api.getPaths().get("/siblings").getPost().getRequestBody()
                    .getContent().get("application/json").getSchema().getProperties().get(property);
            assertEquals(expected, SchemaValidator.hasSiblings(schema));
        }
    }
}
