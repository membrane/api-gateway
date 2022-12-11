package com.predic8.membrane.core.openapi.validators;


import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;

public class ResponseTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void validCustomerResponse() {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidCustomerResponse() {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/invalid-customer.json")));

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

        InputStream is = getResourceAsStream(this, "/openapi/messages/customer.json");

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
    public void wrongMediaTypeResponse() {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200).mediaType("application/xml").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
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

        ValidationErrors errors = validator.validateResponse(Request.post().path("/customers").mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/customer.json")), Response.statusCode(200).mediaType("application/json").body("{ }"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals(500,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("body"));
    }

    @Test
    public void statusCodeNotInResponse() {

        ValidationErrors errors = validator.validateResponse(Request.post().path("/customers").mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/customer.json")), Response.statusCode(202).mediaType("application/json").body("{ }"));
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