package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static org.junit.Assert.*;


public class ReferencesResponseTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/references-response.yml"));
    }

    @Test
    public void refRequestOk()  {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"),Response.statusCode(200)
                .json().body(getResourceAsStream("/openapi/references-requests-responses-customer.json")));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void refResponseInvalid()  {
        InputStream fis = getResourceAsStream("/openapi/references-requests-responses-customer-invalid.json");
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"), Response.statusCode(200, "application/json").body(fis));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(ValidationContext.ValidatedEntityType.BODY,e.getValidationContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getValidationContext().getValidatedEntity());
        assertEquals("string",e.getValidationContext().getSchemaType());
        assertEquals(500,e.getValidationContext().getStatusCode());
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}