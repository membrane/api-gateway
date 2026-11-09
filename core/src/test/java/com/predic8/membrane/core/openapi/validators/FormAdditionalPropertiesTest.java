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

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * additionalProperties of multipart/form-data and application/x-www-form-urlencoded bodies. OAS 3.1
 * parses {@code additionalProperties: false} into a boolean schema, not a Boolean.
 */
class FormAdditionalPropertiesTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/additional-properties.oas31.yaml";
    }

    @Test
    void multipartUnexpectedPart() throws ParseException {
        final var errors = multipart("/multipart-closed", "other", "x");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.getFirst().getMessage().contains("unexpected property 'other'"), () -> "Errors: " + errors);
    }

    @Test
    void multipartUnexpectedPartInlineSchema() throws ParseException {
        final var errors = multipart("/multipart-closed-inline", "other", "x");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void multipartAdditionalPartValid() throws ParseException {
        final var errors = multipart("/multipart-typed", "other", "abc");
        assertEquals(0, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void multipartAdditionalPartInvalid() throws ParseException {
        final var errors = multipart("/multipart-typed", "other", "abcd");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void formUnexpectedField() throws ParseException {
        final var errors = form("/form-closed", "name=Bob&other=x");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.getFirst().getMessage().contains("unexpected property 'other'"), () -> "Errors: " + errors);
    }

    @Test
    void formUnexpectedFieldInlineSchema() throws ParseException {
        final var errors = form("/form-closed-inline", "name=Bob&other=x");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void formAdditionalFieldValid() throws ParseException {
        final var errors = form("/form-typed", "name=Bob&other=abc");
        assertEquals(0, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void formAdditionalFieldInvalid() throws ParseException {
        final var errors = form("/form-typed", "name=Bob&other=abcd");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    private ValidationErrors multipart(String path, String name, String value) throws ParseException {
        return validator.validate(post().path(path)
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(new MultipartBuilder()
                        .part("name", null, TEXT_PLAIN, null, "Bob")
                        .part(name, null, TEXT_PLAIN, null, value)
                        .build()));
    }

    private ValidationErrors form(String path, String body) throws ParseException {
        return validator.validate(post().path(path)
                .mediaType(APPLICATION_X_WWW_FORM_URLENCODED)
                .body(body));
    }
}
