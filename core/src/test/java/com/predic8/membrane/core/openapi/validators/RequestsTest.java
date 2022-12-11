package com.predic8.membrane.core.openapi.validators;


import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.*;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.Assert.*;

public class RequestsTest {

    OpenAPIValidator validator;

    @Before
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void wrongMediaTypeRequest() {

        ValidationErrors errors = validator.validate(Request.post().path("/customers").mediaType("text/plain").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext ctx = errors.get(0).getContext();
        assertEquals(MEDIA_TYPE,ctx.getValidatedEntityType());
        assertEquals("text/plain",ctx.getValidatedEntity());
        assertEquals(415,ctx.getStatusCode());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("mediatype"));
        assertEquals("REQUEST/HEADER/Content-Type", ctx.getLocationForRequest());
    }

    /**
     * The OpenAPI does not specify a content for a request, but payload is sent.
     */
    @Test
    public void noContentInRequestSentPayload() {

        ValidationErrors errors = validator.validate(Request.get().path("/customers").mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getContext().getValidatedEntity());
        assertEquals(400,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("body"));
        assertEquals("REQUEST/BODY", e.getContext().getLocationForRequest());
    }
}