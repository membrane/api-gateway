package com.predic8.membrane.core.openapi;


import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;

public class ResponseTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void validCustomerResponse() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200, "application/json").body(is));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidCustomerResponse() {

        InputStream is = getResourceAsStream("/openapi/invalid-customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200,"application/json").body(is));

//        System.out.println("errors = " + errors);

        assertEquals(3,errors.size());
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntityType().equals(BODY)));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getValidatedEntity().equals("RESPONSE")));
        assertTrue(errors.stream().allMatch(ve -> ve.getValidationContext().getStatusCode() == 500));

        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains("MaxLength")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" maximum ")));
        assertTrue(errors.stream().anyMatch(ve -> ve.toString().contains(" Linden ")));
    }

    @Test
    public void statusCode404() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(404, "application/json").body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getValidationContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getValidationContext().getValidatedEntity());
        assertEquals("object",e.getValidationContext().getSchemaType());
        assertEquals(500,e.getValidationContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("required"));

    }

    @Test
    public void wrongMediaTypeResponse() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers"), Response.statusCode(200,"application/xml").body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(MEDIA_TYPE,e.getValidationContext().getValidatedEntityType());
        assertEquals("application/xml",e.getValidationContext().getValidatedEntity());
        assertEquals(500,e.getValidationContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("mediatype"));
    }

    @Test
    public void wrongMediaTypeRequest() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").mediaType("text/plain").body(is));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(MEDIA_TYPE,e.getValidationContext().getValidatedEntityType());
        assertEquals("text/plain",e.getValidationContext().getValidatedEntity());
        assertEquals(415,e.getValidationContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("mediatype"));
    }



    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}