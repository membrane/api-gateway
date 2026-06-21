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

import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipartFormDataValidatorTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multipart/multipart.oas.yaml";
    }

    /**
     * The 'metadata' part carries a JSON object that is base64 encoded
     * (Content-Transfer-Encoding: base64). It has to be decoded and validated
     * against the referenced Customer schema.
     */
    @Test
    void jsonPartAsBase64() throws ParseException {
        String customer = """
                {"id": 1, "name": "Alice", "email": "alice@example.com"}""";

        var body = new MultipartBuilder()
                .part("metadata", null, APPLICATION_JSON, "base64", encodeBase64(customer))
                .part("file", "data.bin", APPLICATION_OCTET_STREAM , null, "binary-content")
                .build();

        var errors = validateUpload(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    /**
     * The 'file' part is a file upload (type: string, format: binary) that happens
     * to contain a JSON document. As a binary upload its content is opaque, so only
     * its presence is required. The 'metadata' part is validated as JSON.
     */
    @Test
    void jsonAsFileUpload() throws ParseException {
        String json = """
                {"some": "document", "answer": 42}""";

        String body = new MultipartBuilder()
                .part("metadata", null, "application/json", null, """
                        {"id": 2, "name": "Bob"}""")
                .part("file", "payload.json", "application/json", null, json)
                .build();

        var errors = validateUpload(body);

        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    /**
     * A base64 JSON part whose decoded content violates the schema (missing the
     * required 'name') must produce a validation error.
     */
    @Test
    void invalidJsonPartIsReported() throws ParseException {
        var invalidCustomer = """
                {"id": 3}"""; // 'name' is required

        var body = new MultipartBuilder()
                .part("metadata", null, "application/json", "base64", encodeBase64(invalidCustomer))
                .part("file", "data.bin", "application/octet-stream", null, "binary-content")
                .build();

        var errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("name is missing"));
    }

    /**
     * A missing required part ('file') must be reported.
     */
    @Test
    void missingRequiredPartIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part("metadata", null, "application/json", null, """
                        {"id": 4, "name": "Eve"}""")
                .build();

        var errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("file"));
    }

    /**
     * A field name may occur more than once. Every occurrence must be validated; earlier ones
     * must not be silently dropped. Here the first 'metadata' part is invalid (missing the
     * required 'name'), the second is valid - the invalid one still has to be reported.
     */
    @Test
    void repeatedPartOccurrencesAreAllValidated() throws ParseException {
        var body = new MultipartBuilder()
                .part("metadata", null, "application/json", null, """
                        {"id": 5}""")
                .part("metadata", null, "application/json", null, """
                        {"id": 6, "name": "Zoe"}""")
                .part("file", "data.bin", "application/octet-stream", null, "binary-content")
                .build();

        var errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("name is missing"));
    }

    @Nested
    class Xml {

        /**
         * The 'metadataXml' part carries an XML document. It has to be converted to JSON guided by the
         * Customer schema and validated like any other structured part.
         */
        @Test
        void xmlPartIsValidated() throws ParseException {
            var body = new MultipartBuilder()
                    .part("metadata", null, APPLICATION_JSON, null, """
                            {"id": 1, "name": "Alice"}""")
                    .part("metadataXml", null, APPLICATION_XML, null, """
                            <customer><id>2</id><name>Bob</name></customer>""")
                    .part("file", "data.bin", APPLICATION_OCTET_STREAM, null, "binary-content")
                    .build();

            var errors = validateUpload(body);

            assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
        }

        /**
         * An XML part whose content violates the schema (the required 'name' is missing) must be reported.
         */
        @Test
        void invalidXmlPartIsReported() throws ParseException {
            var body = new MultipartBuilder()
                    .part("metadata", null, APPLICATION_JSON, null, """
                            {"id": 1, "name": "Alice"}""")
                    .part("metadataXml", null, APPLICATION_XML, null, """
                            <customer><id>2</id></customer>""") // 'name' is required
                    .part("file", "data.bin", APPLICATION_OCTET_STREAM, null, "binary-content")
                    .build();

            var errors = validateUpload(body);

            assertEquals(1, errors.size(), () -> "Errors: " + errors);
            assertTrue(errors.get(0).getMessage().toLowerCase().contains("name is missing"));
        }

        /**
         * An XML part that is not well-formed must be reported rather than passing validation.
         */
        @Test
        void malformedXmlPartIsReported() throws ParseException {
            var body = new MultipartBuilder()
                    .part("metadata", null, APPLICATION_JSON, null, """
                            {"id": 1, "name": "Alice"}""")
                    .part("metadataXml", null, APPLICATION_XML, null, """
                            <customer><id>2</id><name>Bob</name>""") // not closed
                    .part("file", "data.bin", APPLICATION_OCTET_STREAM, null, "binary-content")
                    .build();

            var errors = validateUpload(body);

            assertEquals(1, errors.size(), () -> "Errors: " + errors);
            assertTrue(errors.get(0).getMessage().contains("Part 'metadataXml'"), () -> "Errors: " + errors);
            assertTrue(errors.get(0).getMessage().toLowerCase().contains("could not be parsed as xml"), () -> "Errors: " + errors);
        }
    }

    /**
     * The encoding declares application/json for 'metadata'. Sending it as text/plain violates the
     * declared encoding and must be reported.
     */
    @Test
    void partContentTypeViolatingEncodingIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part("metadata", null, MimeType.TEXT_PLAIN, null, """
                        {"id": 7, "name": "Mia"}""")
                .part("file", "data.bin", APPLICATION_OCTET_STREAM, null, "binary-content")
                .build();

        var errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("content-type"));
    }

    private ValidationErrors validateUpload(String body) throws ParseException {
        return validator.validate(Request.post().path("/upload")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }

    private static String encodeBase64(String customer) {
        return Base64.getEncoder().encodeToString(customer.getBytes(UTF_8));
    }
}
