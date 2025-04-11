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
import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


class StringTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/strings.yml";
    }

    @Test
    void normal() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("normal","foo"))));
        assertEquals(0,errors.size());
    }

    @Test
    void maxLength() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("maxLength","Müller-Meierddd"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("axLength"));
        assertEquals("/maxLength", e.getContext().getJSONpointer());
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertEquals(400, e.getContext().getStatusCode());
    }

    @Test
    void minLength() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("minLength","a"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("minLength"));
        assertEquals("/minLength", e.getContext().getJSONpointer());
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertEquals(400, e.getContext().getStatusCode());
    }

    @Test
    void uuidValid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("uuid","B7AE38DD-7810-492E-B0BE-DF472F1343E0"))));
        assertEquals(0,errors.size());
    }

    @Test
    void uuidInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("uuid","B7AE38DD-7810-49E-B0BE-DF472F1343E0"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/uuid", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("UUID"));
    }

    @Test
    void emailValid() {
        assertEquals(0, validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("email","foo@bar")))).size());
    }

    @Test
    void emailInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("email","foo"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/email", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("E-Mail"));
    }

    @Test
    void dateValid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("date","2022-11-19"))));
        assertEquals(0,errors.size());
    }

    @Test
    void dateInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("date","2022-02-29"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/date", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("date"));
    }

    @Test
    void dateTimeValid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("date-time","2022-11-19T19:25:00"))));
        assertEquals(0,errors.size());
    }

    @Test
    void dateTimeInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("date-time","2022-02-29"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/date-time", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("date"));
    }

    @Test
    void uriValid() {
        assertEquals(0, validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("uri","a:b")))).size());
    }

    @Test
    void regexValid() {
        assertEquals(0, validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("regex","ABC12")))).size());
    }

    @Test
    void regexInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("regex","AA99"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/regex", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("regex"));
    }

    @Test
    void enumValid() {
        assertEquals(0, validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("enum","Bonn")))).size());
    }

    @Test
    void enumInvalid() {
        ValidationErrors errors = validator.validate(Request.post().path("/strings").body(new JsonBody(getStrings("enum","Stuttgart"))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/enum", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("enum"));
    }

    private JsonNode getStrings(String name, String value) {
        ObjectNode root = om.createObjectNode();
        root.put(name,value);
        return root;
    }
}