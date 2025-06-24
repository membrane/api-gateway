package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PatternPropertiesTest extends AbstractValidatorTest{

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/pattern-properties.yaml";
    }

    @Test
    public void foo() throws ParseException {
        ValidationErrors errors = validator.validate(Request.post().mediaType(APPLICATION_JSON).path("/test").body("""
                {"foo": []}
                """));
        assertEquals(1,errors.size());
        assertEquals("", errors.toString());
    }

}
