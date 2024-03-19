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

public class MultipleContentTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/multiple-content.yml";
    }

    @Test
    public void returnJSON() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType(APPLICATION_JSON).body("""
                {
                    "name": "Alice"
                }
                """));
        assertEquals(0, errors.size());
    }

    @Test
    public void returnXML() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType(APPLICATION_XML).body("""
                <name>Alice</name>
                """));
        assertEquals(0, errors.size());
    }

    @Test
    public void returnAny() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType("*/*").body("""
                {
                    "name": "Alice"
                }
                """));
        assertEquals(0, errors.size());
    }

    @Test
    public void wildcardTest() throws ParseException {
        assertEquals(0, validator.validateResponse(Request.get().path("/with-wildcard"), Response.statusCode(200).mediaType("foo/baz").body("""
                {
                    "name": "Alice"
                }
                """)).size());
    }

    @Test
    public void mediaTypeNotDefined() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/no-wildcard"), Response.statusCode(200).mediaType("foo/baz").body("""
                {
                    "name": "Alice"
                }
                """));
        System.out.println("errors = " + errors);
        assertEquals(0, errors.size());
    }


}