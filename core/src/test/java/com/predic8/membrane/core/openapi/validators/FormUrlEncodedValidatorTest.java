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

import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;

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
    void missingRequiredFieldIsReported() throws ParseException {
        var errors = validateForm("count=3");

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("id"),
                "Expected a missing 'id' error but got: " + errors.get(0).getMessage());
    }

    private ValidationErrors validateForm(String body) throws ParseException {
        return validator.validate(post().path("/form")
                .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body(body));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, UTF_8);
    }
}
