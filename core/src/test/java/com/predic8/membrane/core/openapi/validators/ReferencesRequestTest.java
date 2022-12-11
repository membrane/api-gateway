package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;


public class ReferencesRequestTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/references-request.yml"));
    }

    @Test
    public void refRequestOk()  {
        ValidationErrors errors = validator.validate(Request.post().path("/ref-request").json().body(getResourceAsStream(this, "/openapi/messages/references-requests-responses-customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void refRequestInvalid()  {
        ValidationErrors errors = validator.validate(Request.post().path("/ref-request").json().body(getResourceAsStream(this, "/openapi/messages/references-requests-responses-customer-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getContext().getValidatedEntity());
        assertEquals("string",e.getContext().getSchemaType());
        assertEquals(400,e.getContext().getStatusCode());
        assertEquals("REQUEST/BODY#/name", e.getContext().getLocationForRequest());
    }
}