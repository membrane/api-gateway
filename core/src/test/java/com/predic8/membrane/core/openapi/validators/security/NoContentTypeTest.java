package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.validators.AbstractValidatorTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NoContentTypeTest extends AbstractValidatorTest {


    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/boolean.yml";
    }

    @Test
    public void testNoContentType() {
        assertEquals("POST /boolean : Request has a body, but no Content-Type header.", validator.validate(Request.post().body("foo").path("/boolean")).get(0).toString());
    }
}
