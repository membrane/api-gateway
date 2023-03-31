package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultResponseTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/default-response.yml";
    }

    @Test
    public void valid200Response() {
        assertEquals(0, validator.validateResponse(Request.get().path("/default"), Response.statusCode(200).json().body("""
                "OK"
                """)).size());
    }

    @Test
    public void validDefaultResponse() {
        assertEquals(0, validator.validateResponse(Request.get().path("/default"), Response.statusCode(222).json().body("""
                "Default"
                """)).size());
    }

    @Test
    public void invalidDefaultResponse() {
        ValidationErrors ve = validator.validateResponse(Request.get().path("/default"), Response.statusCode(222).json().body("""
                "Wrong"
                """));
        assertEquals(1, ve.size());
        ValidationError e = ve.get(0);
        assertEquals(ValidationContext.ValidatedEntityType.BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE", e.getContext().getValidatedEntity());
        assertEquals(500, e.getContext().getStatusCode());
    }

    /**
     * To be sure that the 4XX wildcard is prefered
     */
    @Test
    public void response4XX() {
        assertEquals(0, validator.validateResponse(Request.get().path("/default"), Response.statusCode(444).json().body("""
                "Bad Request"
                """)).size());
    }

    /**
     * To be sure that the 5XX wildcard is prefered
     */
    @Test
    public void response5XX() {
        assertEquals(0, validator.validateResponse(Request.get().path("/default"), Response.statusCode(555).json().body("""
                "Server Error"
                """)).size());
    }
}