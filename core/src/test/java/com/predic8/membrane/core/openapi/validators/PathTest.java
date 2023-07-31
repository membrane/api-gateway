package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathTest extends AbstractValidatorTest {
    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/paths/paths.yml";
    }

    @Test
    public void foos() throws ParseException {
        ValidationErrors errors = validator.validate(Request.get().path("/apis/apix/foos"));
        assertEquals(0,errors.size());
    }

    @Test
    public void foosAndId() throws ParseException {
        ValidationErrors errors = validator.validate(Request.get().path("/apis/apix/foos/13"));
        assertEquals(0,errors.size());
    }

    @Test
    public void randomPath() throws ParseException {
        ValidationErrors errors = validator.validate(Request.get().path("/apis/apix/abc"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }
}
