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

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation of scalar multipart parts (delivered as text, the default for primitives) against
 * their property schema, using specs/multipart/encoding-octet-stream.yml where {@code name} is a
 * {@code string} with {@code minLength: 3} and {@code address} is an object encoded as
 * {@code application/octet-stream}.
 */
public class MultipartScalarPartValidationTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/encoding-octet-stream.yml";
    }

    @Test
    void validScalarStringPartPasses() throws ParseException {
        var body = new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Berlin")
                .build();

        var errors = validateAttachments(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void scalarStringViolatingMinLengthIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Al") // minLength is 3
                .build();

        var errors = validateAttachments(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minlength"),
                "Expected a minLength error but got: " + errors.get(0).getMessage());
    }

    @Test
    void octetStreamObjectPartIsTreatedAsOpaque() throws ParseException {
        // The 'address' object is encoded as application/octet-stream, so its content is opaque
        // and only the scalar 'name' is validated.
        var body = new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Berlin")
                .part("address", null, APPLICATION_OCTET_STREAM, null, "any-opaque-bytes")
                .build();

        var errors = validateAttachments(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void addressWithContentTypeViolatingOctetStreamEncodingIsReported() throws ParseException {
        // The encoding declares application/octet-stream for 'address'. Sending the part as
        // application/json violates the declared encoding and must be reported.
        var body = new MultipartBuilder()
                .part("name", null, TEXT_PLAIN, null, "Berlin")
                .part("address", null, APPLICATION_JSON, null, "{\"city\": \"Madrid\"}")
                .build();

        var errors = validateAttachments(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("content-type"),
                "Expected a content-type/encoding mismatch error but got: " + errors.get(0).getMessage());
    }

    private ValidationErrors validateAttachments(String body) throws ParseException {
        return validator.validate(post().path("/v1/attachments")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }
}
