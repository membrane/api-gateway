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

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI32;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAPI32ValidatorTest {

    OpenAPIValidator validator;

    @BeforeEach
    void setUp() {
        var api = parseOpenAPI32(this, "/openapi/specs/oas32/query-method.yaml");
        validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(api, new OpenAPISpec()));
        validator.getApi().setExtensions(new HashMap<>() {{
            put(X_MEMBRANE_VALIDATION, new HashMap<>() {{
                put(SECURITY, true);
                put(REQUESTS, true);
            }});
        }});
    }

    @Test
    void validQueryRequest() {
        ValidationErrors errors = validator.validate(
                new Request<>("QUERY").path("/search").body("""
                        {"term": "shoes", "limit": 5}""").json());
        assertEquals(0, errors.size());
    }

    @Test
    void nullableLimitIsValid() {
        // limit: type [integer, "null"] — a 3.1/3.2 JSON Schema 2020-12 feature parsed with V31 semantics.
        ValidationErrors errors = validator.validate(
                new Request<>("QUERY").path("/search").body("""
                        {"term": "shoes", "limit": null}""").json());
        assertEquals(0, errors.size());
    }

    @Test
    void missingRequiredTermIsInvalid() {
        ValidationErrors errors = validator.validate(
                new Request<>("QUERY").path("/search").body("""
                        {"limit": 5}""").json());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Required"));
    }

    @Test
    void additionalOperationIsRouted() {
        // PURGE is declared via additionalOperations; it has no request body, so it must validate.
        assertEquals(0, validator.validate(new Request<>("PURGE").path("/search")).size());
    }

    @Test
    void methodNotDeclaredIsRejected() {
        ValidationErrors errors = validator.validate(Request.get().path("/search"));
        assertEquals(1, errors.size());
        assertEquals(405, errors.get(0).getContext().getStatusCode());
        assertEquals(METHOD, errors.get(0).getContext().getValidatedEntityType());
    }

    @Test
    void validJsonLinesStreamPasses() throws Exception {
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/bulk").body("""
                        {"id": "a", "title": "A"}
                        {"id": "b"}""").mediaType("application/jsonl"));
        assertEquals(0, errors.size());
    }

    @Test
    void jsonLinesItemMissingRequiredFieldIsInvalid() throws Exception {
        // Each item of the stream is validated against itemSchema; the second item misses "id".
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/bulk").body("""
                        {"id": "a"}
                        {"title": "no id"}""").mediaType("application/jsonl"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Required"));
        assertEquals("/1/id", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void malformedJsonLineIsReported() throws Exception {
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/bulk").body("""
                        {"id": "a"}
                        not-json""").mediaType("application/jsonl"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("cannot be parsed as JSON"));
    }

    @Test
    void validEventStreamPasses() throws Exception {
        // Two Server-Sent Events, each parsed into an object {event, data, retry}.
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/events").body("""
                        event: add
                        data: hello
                        retry: 3000

                        data: world
                        """).mediaType("text/event-stream"));
        assertEquals(0, errors.size());
    }

    @Test
    void validContentSchema() {
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "{\\"event\\": \\"created\\"}"}""").json());
        assertEquals(0, errors.size());
    }

    @Test
    void contentSchemaViolationIsInvalid() {
        // The JSON inside the payload string misses the required "event".
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "{\\"other\\": 1}"}""").json());
        assertEquals(1, errors.size());
        assertEquals("/payload/event", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void contentNotParseableIsInvalid() {
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "not json"}""").json());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("not valid application/json"));
    }

    @Test
    void validBase64ContentSchema() {
        // "eyJpZCI6NX0=" is base64 of {"id":5}
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "{\\"event\\": \\"x\\"}", "encoded": "eyJpZCI6NX0="}""").json());
        assertEquals(0, errors.size());
    }

    @Test
    void base64ContentSchemaViolationIsInvalid() {
        // "eyJpZCI6IngifQ==" is base64 of {"id":"x"} — id must be an integer.
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "{\\"event\\": \\"x\\"}", "encoded": "eyJpZCI6IngifQ=="}""").json());
        assertEquals(1, errors.size());
        assertEquals("/encoded/id", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void validBase64UrlContentSchema() {
        // base64url of {"id": 7, "note": ">>"} — contains '-', which the MIME decoder would skip.
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/wrap").body("""
                        {"payload": "{\\"event\\": \\"x\\"}", "encodedUrl": "eyJpZCI6IDcsICJub3RlIjogIj4-In0="}""").json());
        assertEquals(0, errors.size());
    }

    @Test
    void validQuerystringParameter() {
        ValidationErrors errors = validator.validate(Request.get().path("/find?term=shoes&page=2"));
        assertEquals(0, errors.size());
    }

    @Test
    void querystringMissingRequiredFieldIsInvalid() {
        ValidationErrors errors = validator.validate(Request.get().path("/find?page=2"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("term"));
        assertEquals(400, errors.get(0).getContext().getStatusCode());
    }

    @Test
    void querystringWrongTypeIsInvalid() {
        ValidationErrors errors = validator.validate(Request.get().path("/find?term=shoes&page=notanumber"));
        assertEquals(1, errors.size());
        assertEquals("/page", errors.get(0).getContext().getJSONpointer());
    }

    @Test
    void eventStreamItemMissingDataIsInvalid() throws Exception {
        // The first event has no data line, violating the required "data" of the itemSchema.
        ValidationErrors errors = validator.validate(
                new Request<>("POST").path("/events").body("""
                        event: ping

                        data: world
                        """).mediaType("text/event-stream"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Required property data is missing."));
        assertEquals("/0/data", errors.get(0).getContext().getJSONpointer());
    }
}
