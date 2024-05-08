package com.predic8.membrane.core.openapi.validators.security;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.validators.AbstractValidatorTest;
import org.junit.jupiter.api.Test;

public class NoContentTypeTest  extends AbstractValidatorTest {


    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/boolean.yml";
    }

    @Test
    public void testNoContentType() {
        System.out.println(validator.validate(Request.post().path("/boolean")));
    }
}
