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

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multipart schema is a {@code $ref} to a component. The parser keeps the reference, so the
 * component's required parts, properties and additionalProperties used to be ignored.
 */
class MultipartRefRootTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/ref-root.oas.yaml";
    }

    @Test
    void valid() throws ParseException {
        final var errors = validate(new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Bob")
                .build());
        assertEquals(0, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void missingRequiredPart() throws ParseException {
        final var errors = validate(new MultipartBuilder()
                .part("other", null, TEXT_PLAIN, null, "Bob")
                .build());
        assertTrue(errors.toString().contains("'name' is missing"), () -> "Errors: " + errors);
    }

    @Test
    void unexpectedPart() throws ParseException {
        final var errors = validate(new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Bob")
                .part("other", null, TEXT_PLAIN, null, "x")
                .build());
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.getFirst().getMessage().contains("unexpected property 'other'"), () -> "Errors: " + errors);
    }

    @Test
    void invalidPart() throws ParseException {
        final var errors = validate(new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Robert")
                .build());
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    private ValidationErrors validate(String body) throws ParseException {
        return validator.validate(post().path("/upload")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }
}
