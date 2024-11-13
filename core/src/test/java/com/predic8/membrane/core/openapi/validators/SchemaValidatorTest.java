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

    static ArrayValidator arrayValidator;
    static BooleanValidator booleanValidator;
    static IntegerValidator integerValidator;
    static NumberValidator numberValidator;
    static ObjectValidator objectValidator;

    static StringValidator stringValidator;

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
    @MethodSource("arrayValidatorTestCases")
    void testCanValidateArray(Object input, String expected) {
        String result = arrayValidator.canValidate(input);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("booleanValidatorTestCases")
    void testCanValidateBoolean(Object input, String expected) {
        assertEquals(expected, booleanValidator.canValidate(input));
    }

    @ParameterizedTest
    @MethodSource("integerValidatorTestCases")
    void testCanValidateInteger(Object input, String expected) {
        assertEquals(expected, integerValidator.canValidate(input));
    }

    @ParameterizedTest
    @MethodSource("numberValidatorTestCases")
    void testCanValidateNumber(Object input, String expected) {
        assertEquals(expected, numberValidator.canValidate(input));
    }

    @ParameterizedTest
    @MethodSource("objectValidatorTestCases")
    void testCanValidate(Object input, String expected) {
        if (input instanceof InputStream) {
            assertThrows(RuntimeException.class, () -> objectValidator.canValidate(input), "InputStream should not happen!");
        } else {
            assertEquals(expected, objectValidator.canValidate(input));
        }
    }

    @Test
    void testCanValidateWithInputStream() {
        InputStream inputStream = InputStream.nullInputStream();
        assertThrows(RuntimeException.class, () -> objectValidator.canValidate(inputStream), "InputStream should not happen!");
    }

    @ParameterizedTest
    @MethodSource("stringValidatorTestCases")
    void testCanValidateString(Object input, String expected) {
        assertEquals(expected, stringValidator.canValidate(input));
    }

    private static Stream<Arguments> arrayValidatorTestCases() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nonArrayNode = mapper.createObjectNode().put("key", "value");

        return Stream.of(
                Arguments.of(arrayNode, "array"),
                Arguments.of(nonArrayNode, null),
                Arguments.of("notAnArray", null),
                Arguments.of(null, null)
        );
    }

    private static Stream<Arguments> booleanValidatorTestCases() {
        ObjectMapper mapper = new ObjectMapper();
        BooleanNode booleanNode = BooleanNode.TRUE;
        JsonNode nonBooleanNode = mapper.createObjectNode().put("key", "value");

        return Stream.of(
                Arguments.of(booleanNode, "boolean"),
                Arguments.of("true", "boolean"),
                Arguments.of("false", "boolean"),
                Arguments.of("notABoolean", null),
                Arguments.of(nonBooleanNode, null),
                Arguments.of(null, null)
        );
    }

    private static Stream<Arguments> integerValidatorTestCases() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode intNode = new IntNode(123);
        JsonNode nonIntNode = mapper.createObjectNode().put("key", "value");

        return Stream.of(
                Arguments.of(intNode, "integer"),
                Arguments.of("123", "integer"),
                Arguments.of("notAnInteger", null),
                Arguments.of(123, "integer"),
                Arguments.of(123.45, null),
                Arguments.of(nonIntNode, null),
                Arguments.of(null, null)
        );
    }

    private static Stream<Arguments> numberValidatorTestCases() {
        JsonNode validNumberNode = new TextNode("123.45");
        JsonNode invalidNumberNode = new TextNode("notANumber");

        return Stream.of(
                Arguments.of(validNumberNode, "number"),
                Arguments.of(invalidNumberNode, null),
                Arguments.of("456.78", "number"),
                Arguments.of("invalid", null),
                Arguments.of(123, "number"),
                Arguments.of(null, null)
        );
    }

    private static Stream<Arguments> objectValidatorTestCases() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode objectNode = mapper.createObjectNode();
        JsonNode textNode = new TextNode("stringValue");
        InputStream inputStream = InputStream.nullInputStream();

        return Stream.of(
                Arguments.of(objectNode, "object"),
                Arguments.of(textNode, null),
                Arguments.of(inputStream, null)
        );
    }

    private static Stream<Arguments> stringValidatorTestCases() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stringNode = new TextNode("example");
        JsonNode nonStringNode = mapper.createObjectNode().put("number", 123);

        return Stream.of(
                Arguments.of(stringNode, "string"),
                Arguments.of("example", "string"),
                Arguments.of(nonStringNode, null),
                Arguments.of(null, null),
                Arguments.of(123, null)
        );
    }

}