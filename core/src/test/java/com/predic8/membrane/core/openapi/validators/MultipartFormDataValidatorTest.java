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
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Base64;
import java.util.List;

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

    /**
     * A part without the required Content-Disposition 'name' parameter is malformed and must be
     * rejected rather than silently dropped.
     */
    @Test
    void partWithoutNameIsReported() throws ParseException {
        var body = new MultipartBuilder()
                .part(null, null, APPLICATION_JSON, null, """
                        {"id": 1, "name": "Alice"}""")
                .build();

        var errors = validateUpload(body);

        assertEquals(1, errors.size(), () -> "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("name"), () -> "Errors: " + errors);
    }

    @Test
    void matchingPatternAllowsPartInClosedSchema() throws ParseException {
        var errors = validatePatternPart("x-note", "hello");
        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void matchingPatternValidatesPartValue() throws ParseException {
        var errors = validatePatternPart("x-note", "hi");
        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("minlength"), errors::toString);
        assertEquals("/x-note", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void unmatchedPartRemainsRejectedInClosedSchema() throws ParseException {
        var errors = validatePatternPart("other", "hello");
        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).getMessage().contains("unexpected property 'other'"), errors::toString);
    }

    @Test
    void declaredPropertyAndAllPatternsApplyTogether() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-note", new StringSchema().minLength(5));
        schema.addPatternProperty("^x-", new StringSchema().minLength(4));
        schema.addPatternProperty("note", new StringSchema().minLength(3));
        var errors = validateParts(schema, "x-note", "hi");
        assertEquals(3, errors.size(), errors::toString);
    }

    @Test
    void overlappingTypesUseTheSameDecodedValue() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-count", new IntegerSchema());
        schema.addPatternProperty("^x-", new StringSchema());
        var errors = validateParts(schema, "x-count", "1");
        assertEquals(1, errors.size(), errors::toString);
        assertEquals("/x-count", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void unionContainingStringAcceptsPlainStringPart() throws ParseException {
        var union = new ComposedSchema().oneOf(List.of(new IntegerSchema(), new StringSchema()));
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("value", union);

        var errors = validateParts(schema, "value", "hello");

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void typeUnionAndStringPatternAcceptPlainStringPart() throws ParseException {
        var union = new Schema<>().types(new java.util.LinkedHashSet<>(List.of("array", "string")));
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("value", union);
        schema.addPatternProperty("^value$", new StringSchema().minLength(3));

        assertTrue(validateParts(schema, "value", "hello").isEmpty());
        assertEquals(1, validateParts(schema, "value", "hi").size());
    }

    @Test
    void multiTypePartTriesAnotherRepresentationWhenFirstFailsConstraints() throws ParseException {
        var union = new Schema<>().types(new java.util.LinkedHashSet<>(List.of("string", "integer")))
                .minLength(2).minimum(java.math.BigDecimal.TWO);
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("value", union);
        schema.addPatternProperty("^value$", new Schema<>().types(
                new java.util.LinkedHashSet<>(List.of("string", "integer"))));

        var valid = validateParts(schema, "value", "2");
        assertTrue(valid.isEmpty(), valid::toString);
        assertTrue(!validateParts(schema, "value", "1").isEmpty());
    }

    @Test
    void candidateMustSatisfyEveryMatchingSchema() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("value", new Schema<>().types(new java.util.LinkedHashSet<>(List.of("string", "integer")))
                .minLength(2));
        schema.addPatternProperty("^value$", new IntegerSchema().minimum(java.math.BigDecimal.TEN));

        assertTrue(validateParts(schema, "value", "10").isEmpty());
        assertTrue(!validateParts(schema, "value", "2").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void patternOnlyDecodingIsIndependentOfInsertionOrder(boolean reverse) throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        if (reverse)
            schema.addPatternProperty("count$", new StringSchema());
        schema.addPatternProperty("^x-", new IntegerSchema());
        schema.addPatternProperty("count$", new StringSchema());
        assertEquals(1, validateParts(schema, "x-count", "1").size());
    }

    @Test
    void patternArrayValidatesEveryOccurrenceAndCardinality() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addPatternProperty("^x-", new ArraySchema().items(new IntegerSchema()).maxItems(2));
        assertTrue(validateParts(schema, "x-count", "1", "2").isEmpty());
        assertEquals(1, validateParts(schema, "x-count", "1", "bad").size());
        assertEquals(1, validateParts(schema, "x-count", "1", "2", "3").size());
    }

    @Test
    void patternArrayValidatesUniqueItemsAcrossOccurrences() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addPatternProperty("^x-", new ArraySchema().items(new IntegerSchema()).uniqueItems(true));

        assertTrue(validateParts(schema, "x-count", "1", "2").isEmpty());
        var errors = validateParts(schema, "x-count", "1", "1");
        assertTrue(!errors.isEmpty(), "Duplicate array items must be rejected");
    }

    @Test
    void typelessPatternConstrainsTheWholeArray() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-count", new ArraySchema().items(new IntegerSchema()));
        schema.addPatternProperty("^x-", new Schema<>().uniqueItems(true));

        assertTrue(validateParts(schema, "x-count", "1", "2").isEmpty());
        assertTrue(!validateParts(schema, "x-count", "1", "1").isEmpty(),
                "A typeless schema must reject duplicate array items");
        assertTrue(!validateParts(schema, "x-count", "bad").isEmpty(),
                "Individual items must still be validated");
    }

    @Test
    void arrayPatternWithoutItemsOnlyConstrainsCardinality() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-count", new ArraySchema().items(new IntegerSchema()));
        schema.addPatternProperty("^x-", new ArraySchema().maxItems(1));
        assertTrue(validateParts(schema, "x-count", "1").isEmpty());
        assertEquals(1, validateParts(schema, "x-count", "1", "2").size());
    }

    @Test
    void arrayCannotSatisfyScalarPatternByValidatingItsItems() throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addProperty("x-count", new ArraySchema().items(new IntegerSchema()));
        schema.addPatternProperty("^x-", new IntegerSchema());
        assertEquals(1, validateParts(schema, "x-count", "1").size());
    }

    private ValidationErrors validatePatternPart(String name, String value) throws ParseException {
        var schema = new ObjectSchema().additionalProperties(false);
        schema.addPatternProperty("^x-", new StringSchema().minLength(3));
        return validateParts(schema, name, value);
    }

    private ValidationErrors validateParts(Schema<?> schema, String name, String... values) throws ParseException {
        var builder = new MultipartBuilder();
        for (var value : values)
            builder.part(name, null, TEXT_PLAIN, null, value);
        return new MultipartFormDataValidator(new OpenAPI()).validate(
                new ValidationContext().entity("REQUEST"), new MediaType().schema(schema),
                Request.post().path("/upload").mediaType(MultipartBuilder.CONTENT_TYPE).body(builder.build()));
    }

    private ValidationErrors validateUpload(String body) throws ParseException {
        return validator.validate(Request.post().path("/upload")
                .mediaType(MultipartBuilder.CONTENT_TYPE)
                .body(body));
    }

    /**
     * Each occurrence of an array property is validated against the prefixItems schema of its position,
     * otherwise against items. See #3499.
     */
    @Nested
    class PrefixItems extends AbstractValidatorTest {

        @Override
        protected String getOpenAPIFileName() {
            return "/openapi/specs/oas31/prefix-items.yaml";
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "rest|1,a",
                "rest|1,a,true,false",
                "open|1,a,true",
                "closed|1"})
        void accepted(String name, String values) throws ParseException {
            final var errors = validateParts(name, values);
            assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
        }

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "rest|a,b|1",
                "rest|1,a,x|1",
                "open|a|1",
                "closed|1,a|1",
                "closed|1,a,b|2"})
        void rejected(String name, String values, int count) throws ParseException {
            final var errors = validateParts(name, values);
            assertEquals(count, errors.size(), errors::toString);
        }

        private ValidationErrors validateParts(String name, String values) throws ParseException {
            final var builder = new MultipartBuilder();
            for (final var value : List.of(values.split(",")))
                builder.part(name, null, TEXT_PLAIN, null, value);
            return validator.validate(Request.post().path("/multipart")
                    .mediaType(MultipartBuilder.CONTENT_TYPE)
                    .body(builder.build()));
        }
    }

    private static String encodeBase64(String customer) {
        return Base64.getEncoder().encodeToString(customer.getBytes(UTF_8));
    }
}
