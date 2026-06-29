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
 * The OAS 3.1.1 "Basic Multipart Form" example (specs/multipart/no-encoding.oas.yaml) has no
 * {@code encoding} section, so the per-property default content types apply:
 * <ul>
 *   <li>{@code id} – string with {@code format: uuid} → text/plain (scalar, validated)</li>
 *   <li>{@code profileImage} – string with {@code format: binary} → application/octet-stream (opaque)</li>
 *   <li>{@code addresses} – array of objects → application/json</li>
 * </ul>
 */
public class MultipartNoEncodingDefaultsTest extends AbstractValidatorTest {

    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/no-encoding.oas.yaml";
    }

    @Test
    void validFormWithAllDefaultsPasses() throws ParseException {
        // Each array item is its own part with the same field name. The parts carry no Content-Type,
        // so the defaults apply: text/plain for 'id', application/json for the object 'addresses' items.
        var body = new MultipartBuilder()
                .part("id", null, null, null, VALID_UUID)
                .part("profileImage", "avatar.png", APPLICATION_OCTET_STREAM, null, "binary-image-bytes")
                .part("addresses", null, null, null, "{\"street\": \"Main\", \"city\": \"Berlin\"}")
                .part("addresses", null, null, null, "{\"street\": \"Second\", \"city\": \"Madrid\"}")
                .build();

        var errors = validateAttachments(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void invalidUuidScalarIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part("id", null, TEXT_PLAIN, null, "not-a-uuid")
                .build();

        var errors = validateAttachments(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("uuid"),
                "Expected a uuid format error but got: " + errors.get(0).getMessage());
    }

    @Test
    void binaryProfileImageIsTreatedAsOpaque() throws ParseException {
        var body = new MultipartBuilder()
                .part("profileImage", "avatar.png", APPLICATION_OCTET_STREAM, null, "any-opaque-bytes")
                .build();

        var errors = validateAttachments(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    void eachArrayItemPartIsValidatedAgainstItemsSchema() throws ParseException {
        // Two 'addresses' parts (one item each); the second has a non-string 'city'. Each part is
        // validated against the items (Address) schema, so exactly one error is reported.
        var body = new MultipartBuilder()
                .part("addresses", null, APPLICATION_JSON, null, "{\"street\": \"Main\", \"city\": \"Berlin\"}")
                .part("addresses", null, APPLICATION_JSON, null, "{\"city\": 123}")
                .build();

        var errors = validateAttachments(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("string"),
                "Expected a type error for 'city' but got: " + errors.get(0).getMessage());
    }

    private ValidationErrors validateAttachments(String body) throws ParseException {
        return validator.validate(post().path("/v1/attachments")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }
}
