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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


public class BooleanTest {

    OpenAPIValidator validator;
    private final static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/specs/boolean.yml"));
    }

    @Test
    public void validInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/boolean").json().body(new JsonBody(getBoolean("good",true))));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void inValidInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/boolean").body(new JsonBody(getStrings("good","abc"))));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/good", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("boolean"));
        assertEquals("REQUEST/BODY#/good", e.getContext().getLocationForRequest());

    }

    @Test
    public void validInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/boolean?truth=true"));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void inValidInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/boolean?truth=abc"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER, e.getContext().getValidatedEntityType());
        assertEquals("truth", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("boolean"));
        assertEquals("REQUEST/QUERY_PARAMETER/truth", e.getContext().getLocationForRequest());
    }

    private JsonNode getBoolean(String name, boolean bool) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(name,bool);
        return root;
    }

    private JsonNode getStrings(String name, String value) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(name,value);
        return root;
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}