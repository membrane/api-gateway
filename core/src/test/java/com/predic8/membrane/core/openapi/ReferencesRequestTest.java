package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;


public class ReferencesRequestTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/references-request.yml"));
    }

    @Test
    public void refRequestOk()  {
        InputStream fis = getResourceAsStream("/openapi/references-requests-responses-customer.json");
        ValidationErrors errors = validator.validate(Request.post().path("/ref-request").body(fis));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void refRequestInvalid()  {
        InputStream fis = getResourceAsStream("/openapi/references-requests-responses-customer-invalid.json");
        ValidationErrors errors = validator.validate(Request.post().path("/ref-request").body(fis));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getValidationContext().getValidatedEntity());
        assertEquals("string",e.getValidationContext().getSchemaType());
        assertEquals(400,e.getValidationContext().getStatusCode());
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}