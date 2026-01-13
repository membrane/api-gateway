package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

class StringValidatorTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final JsonNode JSON_NUMBER = OM.getNodeFactory().numberNode(12);
    private static final JsonNode JSON_BOOLEAN = OM.getNodeFactory().booleanNode(true);
    private static final JsonNode JSON_STRING = OM.getNodeFactory().textNode("abc");
    private static final JsonNode JSON_NULL = OM.getNodeFactory().nullNode();
    private static final JsonNode JSON_ARRAY = OM.getNodeFactory().arrayNode();
    private static final JsonNode JSON_OBJECT = OM.getNodeFactory().objectNode();
    private static final JsonNode JSON_EMPTY_STRING = OM.getNodeFactory().textNode("");

    private static final StringValidator STRING_VALIDATOR = new StringValidator(new Schema<>());

    @Nested
    class Normal {

        ValidationContext ctx;

        @BeforeEach
        void setUp() {
            ctx = new ValidationContext().entityType(BODY);
        }

        @Test
        void canValidateString() {
            assertEquals(STRING, STRING_VALIDATOR.canValidate("abc"));
            assertEquals(STRING, STRING_VALIDATOR.canValidate(JSON_STRING));
            assertEquals(null, STRING_VALIDATOR.canValidate(JSON_NUMBER));
        }

        @Test
        void number() {
            var err = STRING_VALIDATOR.validate(ctx, JSON_NUMBER);
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("String expected"));
            assertTrue(err.get(0).getMessage().contains("NUMBER"));
        }

        @Test
        void booleanValue() {
            var err = STRING_VALIDATOR.validate(ctx, JSON_BOOLEAN);
            assertEquals(1, err.size());
            assertTrue(err.get(0).getMessage().contains("String expected"));
            assertTrue(err.get(0).getMessage().contains("BOOLEAN"));
        }

        @Test
        void string() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_STRING).hasErrors());
        }

        @Test
        void emptyString() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_EMPTY_STRING).hasErrors());
        }
    }

    @Nested
    class QueryString {

        ValidationContext ctx;

        @BeforeEach
        void setUp() {
            ctx = new ValidationContext().entityType(QUERY_PARAMETER);
        }

        @Test
        void number() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_NUMBER).hasErrors());
        }

        @Test
        void booleanValue() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_BOOLEAN).hasErrors());
        }

        @Test
        void string() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_STRING).hasErrors());
        }

        @Test
        void emptyString() {
            assertFalse(STRING_VALIDATOR.validate(ctx, JSON_EMPTY_STRING).hasErrors());
        }

        @Test
        void nullValue() {
            assertEquals(0, STRING_VALIDATOR.validate(ctx, JSON_NULL).size());
        }

        @Test
        void array() {
            assertEquals(0, STRING_VALIDATOR.validate(ctx, JSON_ARRAY).size());
        }

        @Test
        void object() {
            assertEquals(0, STRING_VALIDATOR.validate(ctx, JSON_OBJECT).size());
        }
    }
}