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

/**
 * The Content-Type of a part is client-controlled. It must not select a path that skips the schema
 * of a structured (object/array) part.
 */
class MultipartPartContentTypeTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/part-content-type.oas.yaml";
    }

    @Test
    void jsonPartValid() throws ParseException {
        final var errors = meta("/encoded", APPLICATION_JSON, "{\"id\": 1}");
        assertEquals(0, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void wildcardSubtypeMatchingEncoding() throws ParseException {
        final var errors = meta("/encoded", "application/*", "garbage");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void wildcardWithoutEncoding() throws ParseException {
        final var errors = meta("/plain", "*/*", "garbage");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void octetStreamForObjectPart() throws ParseException {
        final var errors = meta("/plain", APPLICATION_OCTET_STREAM, "garbage");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void textPlainForObjectPart() throws ParseException {
        final var errors = meta("/plain", TEXT_PLAIN, "{\"id\": \"x\"}");
        assertEquals(1, errors.size(), () -> "Errors: " + errors);
    }

    @Test
    void binaryPartStaysOpaque() throws ParseException {
        final var errors = validator.validate(post().path("/plain")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(new MultipartBuilder()
                        .part("file", "a.bin", APPLICATION_OCTET_STREAM, null, "any-bytes")
                        .build()));
        assertEquals(0, errors.size(), () -> "Errors: " + errors);
    }

    private ValidationErrors meta(String path, String contentType, String content) throws ParseException {
        return validator.validate(post().path(path)
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(new MultipartBuilder()
                        .part("meta", null, contentType, null, content)
                        .build()));
    }
}
