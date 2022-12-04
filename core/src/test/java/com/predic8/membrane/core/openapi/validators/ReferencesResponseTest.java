package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static org.junit.Assert.*;


public class ReferencesResponseTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this,"/openapi/references-response.yml"));
    }

    @Test
    public void refRequestOk()  {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"),Response.statusCode(200)
                .json().body(getResourceAsStream(this,"/openapi/references-requests-responses-customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void refResponseInvalid()  {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"), Response.statusCode(200).mediaType("application/json").body(getResourceAsStream(this,"/openapi/references-requests-responses-customer-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(ValidationContext.ValidatedEntityType.BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals("string",e.getContext().getSchemaType());
        assertEquals(500,e.getContext().getStatusCode());
        assertEquals("RESPONSE/BODY/name", e.getContext().getLocationForResponse());
    }
}