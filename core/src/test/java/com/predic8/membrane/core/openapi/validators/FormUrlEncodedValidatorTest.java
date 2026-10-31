/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URLEncoder;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation of {@code application/x-www-form-urlencoded} request bodies (specs/x-www-form-urlencoded/form.yml):
 * scalar fields (incl. {@code format}), a stringified-JSON object field, and array fields via repeated keys.
 */
public class FormUrlEncodedValidatorTest extends AbstractValidatorTest {

    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/x-www-form-urlencoded/form.yml";
    }

    @Test
    void validFormPasses() throws ParseException {
        String body = "id=%s&count=3&address=%s&tags=red&tags=green&counts=1&counts=2".formatted(VALID_UUID, enc("""
                {"city":"Berlin","country":"DEU"}"""));

        var errors = validateForm(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void invalidUuidScalarIsReported() throws ParseException {
        var errors = validateForm("id=not-a-uuid");

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("uuid"),
                "Expected a uuid format error but got: " + errors.get(0).getMessage());
    }

    @Test
    void nonIntegerScalarIsReported() throws ParseException {
        var errors = validateForm("id=%s&count=abc".formatted(VALID_UUID));
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void invalidFieldInStringifiedJsonObjectIsReported() throws ParseException {
        // address.country has minLength 3; "DE" violates it.
        var errors = validateForm("id=" + VALID_UUID + "&address=" + enc("""
                {"city":"Berlin","country":"DE"}"""));

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minlength"),
                "Expected a minLength error but got: " + errors.get(0).getMessage());
    }

    @Test
    void invalidArrayItemIsReported() throws ParseException {
        // counts is an integer array; the second value is not an integer.
        var errors = validateForm("id=%s&counts=1&counts=two".formatted(VALID_UUID));

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void minLengthOnTopLevelStringViaRefIsReported() throws ParseException {
        // The request body schema is a $ref; 'code' is a top-level string with minLength 5.
        var errors = validateForm("id=" + VALID_UUID + "&code=abc");

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minlength"),
                "Expected a minLength error but got: " + errors.get(0).getMessage());
    }

    @Test
    void missingRequiredFieldIsReported() throws ParseException {
        var errors = validateForm("count=3");

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("id"),
                "Expected a missing 'id' error but got: " + errors.get(0).getMessage());
    }

    // -----------------------------------------------------------------------
    // Same validation must work for an inline top-level schema (no $ref)
    // -----------------------------------------------------------------------

    @Test
    void validInlineFormPasses() throws ParseException {
        var errors = validateInline("id=" + VALID_UUID + "&code=abcde");

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void minLengthOnTopLevelStringInlineIsReported() throws ParseException {
        var errors = validateInline("id=" + VALID_UUID + "&code=abc");

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minlength"),
                "Expected a minLength error but got: " + errors.get(0).getMessage());
    }

    /**
     * A repeated key is converted item by item with the prefixItems schema of its position, otherwise
     * with items. See #3499.
     */
    @Nested
    class PrefixItems extends AbstractValidatorTest {

        @Override
        protected String getOpenAPIFileName() {
            return "/openapi/specs/oas31/prefix-items.yaml";
        }

        @ParameterizedTest
        @CsvSource({
                "closed=1&closed=a",
                "closed=1",
                "open=1&open=a&open=true"})
        void accepted(String body) throws ParseException {
            final var errors = validatePrefixItems(body);
            assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "closed=a&closed=1|/closed/0",
                "closed=1&closed=a&closed=2|/closed/2",
                "open=a|/open/0"})
        void rejected(String body, String pointer) throws ParseException {
            final var errors = validatePrefixItems(body);
            assertEquals(List.of(pointer), errors.stream().map(e -> e.getContext().getJSONpointer()).toList(), errors::toString);
        }

        private ValidationErrors validatePrefixItems(String body) throws ParseException {
            return validator.validate(post().path("/form")
                    .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                    .body(body));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "x-count=3,0",
            "x-count=abc,1",
            "other=3,1",
            "prefix-x-count=3,0",
            "x-count=3&other=3,1"
    })
    void patternPropertiesWithClosedSchema(String body, int expectedErrors) {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addPatternProperty("x-", new IntegerSchema());
        var errors = new FormUrlEncodedValidator(new OpenAPI()).validate(
                new ValidationContext(), new MediaType().schema(schema), body);
        assertEquals(expectedErrors, errors.size(), errors::toString);
    }

    @Test
    void allMatchingPatternsAndDeclaredPropertiesAreValidated() {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-name", new StringSchema().minLength(5));
        schema.addPatternProperty("^x-", new StringSchema().minLength(4));
        schema.addPatternProperty("name$", new StringSchema().minLength(3));
        var errors = new FormUrlEncodedValidator(new OpenAPI()).validate(
                new ValidationContext(), new MediaType().schema(schema), "x-name=a");
        assertEquals(3, errors.size(), errors::toString);
        assertTrue(errors.stream().allMatch(e -> "/x-name".equals(e.getContext().getJSONpointer())));
    }

    private ValidationErrors validateForm(String body) throws ParseException {
        return validator.validate(post().path("/form")
                .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body(body));
    }

    private ValidationErrors validateInline(String body) throws ParseException {
        return validator.validate(post().path("/form-inline")
                .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body(body));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, UTF_8);
    }
}
