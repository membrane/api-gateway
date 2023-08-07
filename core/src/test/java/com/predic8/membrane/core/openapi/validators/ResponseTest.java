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


import com.predic8.membrane.core.openapi.model.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResponseTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/customers.yml";
    }

    @Test
    public void validCustomerResponse() {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).json().body(getResourceAsStream("/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidCustomerResponse() {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).json().body(getResourceAsStream("/openapi/messages/invalid-customer.json")));

//        System.out.println("errors = " + errors);

        assertEquals(3,errors.size());
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getValidatedEntityType().equals(BODY)));
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getValidatedEntity().equals("RESPONSE")));
        assertTrue(errors.stream().allMatch(ve -> ve.getContext().getStatusCode() == 500));

        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains("MaxLength")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" maximum ")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" Linden ")));
    }

    @Test
    public void statusCode404() {

        InputStream is = getResourceAsStream("/openapi/messages/customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(404).json().body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals("object",e.getContext().getSchemaType());
        assertEquals(500,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("required"));

    }

    @Test
    public void wrongMediaTypeResponse() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).mediaType(APPLICATION_XML).body(getResourceAsStream("/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(MEDIA_TYPE,e.getContext().getValidatedEntityType());
        assertEquals("application/xml",e.getContext().getValidatedEntity());
        assertEquals(500,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("mediatype"));
    }

    /**
     * The OpenAPI does not specify a content for a response, but der backend is sending a body.
     */
    @Test
    public void noContentInResponseSendPayload() {

        ValidationErrors errors = validator.validateResponse(Request.post().path("/customers").json().body(getResourceAsStream("/openapi/messages/customer.json")), Response.statusCode(200).json().body("{ }"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals(500,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("body"));
    }

    @Test
    public void statusCodeNotInResponse() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.post().path("/customers").json().body(getResourceAsStream("/openapi/messages/customer.json")), Response.statusCode(202).mediaType(APPLICATION_JSON).body("{ }"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("POST",e.getContext().getMethod());
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals(500,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("status"));
    }
}