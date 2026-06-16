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

import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipartFormDataValidatorTest extends AbstractValidatorTest {

    private static final String BOUNDARY = "abc123";
    private static final String CRLF = "\r\n";

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart.yml";
    }

    /**
     * The 'metadata' part carries a JSON object that is base64 encoded
     * (Content-Transfer-Encoding: base64). It has to be decoded and validated
     * against the referenced Customer schema.
     */
    @Test
    void jsonPartAsBase64() throws ParseException {
        String customer = "{\"id\": 1, \"name\": \"Alice\", \"email\": \"alice@example.com\"}";
        String base64 = Base64.getEncoder().encodeToString(customer.getBytes(UTF_8));

        String body = new MultipartBuilder()
                .part("metadata", null, "application/json", "base64", base64)
                .part("file", "data.bin", "application/octet-stream", null, "binary-content")
                .build();

        ValidationErrors errors = validateUpload(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    /**
     * The 'file' part is a file upload (type: string, format: binary) that happens
     * to contain a JSON document. As a binary upload its content is opaque, so only
     * its presence is required. The 'metadata' part is validated as JSON.
     */
    @Test
    void jsonAsFileUpload() throws ParseException {
        String json = "{\"some\": \"document\", \"answer\": 42}";

        String body = new MultipartBuilder()
                .part("metadata", null, "application/json", null, "{\"id\": 2, \"name\": \"Bob\"}")
                .part("file", "payload.json", "application/json", null, json)
                .build();

        ValidationErrors errors = validateUpload(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    /**
     * A base64 JSON part whose decoded content violates the schema (missing the
     * required 'name') must produce a validation error.
     */
    @Test
    void invalidJsonPartIsReported() throws ParseException {
        String invalidCustomer = "{\"id\": 3}"; // 'name' is required
        String base64 = Base64.getEncoder().encodeToString(invalidCustomer.getBytes(UTF_8));

        String body = new MultipartBuilder()
                .part("metadata", null, "application/json", "base64", base64)
                .part("file", "data.bin", "application/octet-stream", null, "binary-content")
                .build();

        ValidationErrors errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("name"));
    }

    /**
     * A missing required part ('file') must be reported.
     */
    @Test
    void missingRequiredPartIsReported() throws ParseException {
        String body = new MultipartBuilder()
                .part("metadata", null, "application/json", null, "{\"id\": 4, \"name\": \"Eve\"}")
                .build();

        ValidationErrors errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("file"));
    }

    private ValidationErrors validateUpload(String body) throws ParseException {
        return validator.validate(Request.post().path("/upload")
                .mediaType("multipart/form-data; boundary=" + BOUNDARY)
                .body(body));
    }

    /**
     * Assembles a raw multipart/form-data body using CRLF line breaks as required
     * by the multipart parser.
     */
    private static class MultipartBuilder {

        private final StringBuilder sb = new StringBuilder();

        MultipartBuilder part(String name, String filename, String contentType, String transferEncoding, String content) {
            sb.append("--").append(BOUNDARY).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"");
            if (filename != null)
                sb.append("; filename=\"").append(filename).append("\"");
            sb.append(CRLF);
            if (contentType != null)
                sb.append("Content-Type: ").append(contentType).append(CRLF);
            if (transferEncoding != null)
                sb.append("Content-Transfer-Encoding: ").append(transferEncoding).append(CRLF);
            sb.append(CRLF);
            sb.append(content).append(CRLF);
            return this;
        }

        String build() {
            return sb + "--" + BOUNDARY + "--" + CRLF;
        }
    }
}
