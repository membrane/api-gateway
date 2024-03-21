/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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
import com.predic8.membrane.core.openapi.model.Response;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultipleContentTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multiple-content.yml";
    }

    @Test
    public void sendJSONRequest() throws ParseException {

        ValidationErrors errors = validator.validate(Request.put().path("/with-wildcard").mediaType(APPLICATION_JSON).body("""
                {
                    "name": "Alice"
                }
                """));
        assertEquals(0, errors.size());
    }

    @Test
    public void returnJSONResponse() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType(APPLICATION_JSON).body("""
                {
                    "name": "Alice"
                }
                """));
        assertEquals(0, errors.size());
    }

    @Test
    public void sendXMLRequest() throws ParseException {
        ValidationErrors errors = validator.validate(Request.put().path("/with-wildcard").mediaType(APPLICATION_XML).body("""
                <name>Alice</name>
                """));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(400, error.getContext().getStatusCode());
        assertTrue(error.getMessage().contains("not implemented"));
    }

    @Test
    public void returnXMLResponse() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType(APPLICATION_XML).body("""
                <name>Alice</name>
                """));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(500, error.getContext().getStatusCode());
        assertTrue(error.getMessage().contains("not implemented"));
    }

    @Test
    public void sendAnyRequest() throws ParseException {
        ValidationErrors errors = validator.validate(Request.put().path("/with-wildcard").mediaType("*/*").body("{}"));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(400, error.getContext().getStatusCode());
        assertTrue(error.getMessage().contains("not concrete"));
    }

    @Test
    public void returnAny() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType("*/*").body("{}"));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(500, error.getContext().getStatusCode());
        assertTrue(error.getMessage().contains("not concrete"));
    }

    @Test
    public void wildcardTestRequest() throws ParseException {
        assertEquals(0, validator.validate(Request.put().path("/with-wildcard").mediaType("foo/baz").body("{}")).size());
    }

    @Test
    public void wildcardTestResponse() throws ParseException {
        assertEquals(0, validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType("foo/baz").body("""
                {
                    "name": "Alice"
                }
                """)).size());
    }

    @Test
    public void mediaTypeNotDefinedRequest() throws ParseException {
        ValidationErrors errors = validator.validate(Request.put().path("/no-wildcard").mediaType("foo/baz").body("Bar"));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getMessage().contains("Content-Type"));
        assertTrue(error.getMessage().contains("does not"));
        assertTrue(error.getMessage().contains("[application/json, application/xml]"));
    }

    @Test
    public void mediaTypeNotDefinedResponse() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/no-wildcard"), Response.statusCode(200).mediaType("foo/baz").body("Bar"));
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getMessage().contains("Content-Type"));
        assertTrue(error.getMessage().contains("does not"));
        assertTrue(error.getMessage().contains("[application/json, application/xml]"));
    }
}