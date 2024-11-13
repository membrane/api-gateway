package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SchemaValidatorTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    static IJSONSchemaValidator arrayValidator;
    static IJSONSchemaValidator booleanValidator;
    static IJSONSchemaValidator integerValidator;
    static IJSONSchemaValidator numberValidator;
    static IJSONSchemaValidator objectValidator;
    static IJSONSchemaValidator stringValidator;

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
    void testCanValidate(IJSONSchemaValidator validator, Object input, String expected) {
        if (input instanceof InputStream) {
            assertThrows(RuntimeException.class, () -> validator.canValidate(input), "InputStream should not happen!");
        } else {
            assertEquals(expected, validator.canValidate(input));
        }
    }

    private static Stream<Arguments> validatorTestCases() {
        JsonNode nonArrayNode = mapper.createObjectNode().put("key", "value");
        JsonNode stringNode = new TextNode("example");

        return Stream.of(
                // ArrayValidator test cases
                Arguments.of(arrayValidator, mapper.createArrayNode(), "array"),
                Arguments.of(arrayValidator, nonArrayNode, null),
                Arguments.of(arrayValidator, "notAnArray", null),
                Arguments.of(arrayValidator, null, null),

                // BooleanValidator test cases
                Arguments.of(booleanValidator, BooleanNode.TRUE, "boolean"),
                Arguments.of(booleanValidator, "true", "boolean"),
                Arguments.of(booleanValidator, "false", "boolean"),
                Arguments.of(booleanValidator, "notABoolean", null),
                Arguments.of(booleanValidator, nonArrayNode, null),
                Arguments.of(booleanValidator, null, null),

                // IntegerValidator test cases
                Arguments.of(integerValidator, new IntNode(123), "integer"),
                Arguments.of(integerValidator, "123", "integer"),
                Arguments.of(integerValidator, "notAnInteger", null),
                Arguments.of(integerValidator, 123, "integer"),
                Arguments.of(integerValidator, 123.45, null),
                Arguments.of(integerValidator, nonArrayNode, null),
                Arguments.of(integerValidator, null, null),

                // NumberValidator test cases
                Arguments.of(numberValidator, new TextNode("123.45"), "number"),
                Arguments.of(numberValidator, new TextNode("notANumber"), null),
                Arguments.of(numberValidator, "456.78", "number"),
                Arguments.of(numberValidator, "invalid", null),
                Arguments.of(numberValidator, 123, "number"),
                Arguments.of(numberValidator, null, null),

                // ObjectValidator test cases
                Arguments.of(objectValidator, mapper.createObjectNode(), "object"),
                Arguments.of(objectValidator, stringNode, null),
                Arguments.of(objectValidator, InputStream.nullInputStream(), null),

                // StringValidator test cases
                Arguments.of(stringValidator, stringNode, "string"),
                Arguments.of(stringValidator, "example", "string"),
                Arguments.of(stringValidator, mapper.createObjectNode().put("number", 123), null),
                Arguments.of(stringValidator, null, null),
                Arguments.of(stringValidator, 123, null)
        );
    }

    @Test
    void testCanValidateWithInputStream() {
        InputStream inputStream = InputStream.nullInputStream();
        assertThrows(RuntimeException.class, () -> objectValidator.canValidate(inputStream), "InputStream should not happen!");
    }
}
