package com.predic8.membrane.core.openapi;


import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import org.junit.*;

import java.io.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;

public class RequestsTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream("/openapi/customers.yml"));
    }

    @Test
    public void wrongMediaTypeRequest() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validate(Request.post().path("/customers").mediaType("text/plain").body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(MEDIA_TYPE,e.getValidationContext().getValidatedEntityType());
        assertEquals("text/plain",e.getValidationContext().getValidatedEntity());
        assertEquals(415,e.getValidationContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("mediatype"));
    }

    /**
     * The OpenAPI does not specify a content for a request, but payload is sent.
     */
    @Test
    public void noContentInRequestSentPayload() {

        InputStream is = getResourceAsStream("/openapi/customer.json");

        ValidationErrors errors = validator.validate(Request.get().path("/customers").mediaType("application/json").body(is));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getValidationContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getValidationContext().getValidatedEntity());
        assertEquals(400,e.getValidationContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("body"));
    }

    private InputStream getResourceAsStream(String fileName) {
        return this.getClass().getResourceAsStream(fileName);
    }

}