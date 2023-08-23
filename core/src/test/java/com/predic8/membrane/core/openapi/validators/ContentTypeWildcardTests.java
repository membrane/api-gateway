package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.model.Request;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.mapToJson;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentTypeWildcardTests extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/content-type-wildcards.yml";
    }

    // See https://datatracker.ietf.org/doc/html/rfc7231#appendix-D
    // media-range = ( "*/*" / ( type "/*" ) / ( type "/" subtype ) ) *( OWS
    //    ";" OWS parameter )
    // For that reason java.jakarta.ContentType does not match for type = *
    // In Message.isOfMediaType we fix that for Membrane
    @Test
    void contentTypeMatching() throws ParseException {
        ContentType ct = new ContentType("*/*");
        assertFalse(ct.match(MimeType.APPLICATION_JSON));
    }

    @Test
    void contentTypeMatchingSwitch() throws ParseException {
        ContentType ct = new ContentType(MimeType.APPLICATION_JSON);
        assertTrue(ct.match("application/*"));
    }

    @Test
    void starStarTest() {
        ValidationErrors errors = validator.validate(Request.post().json().path("/star-star").body("{}"));
        assertTrue(errors.isEmpty());
    }

    @Test
    void starTypeTest() {
        ValidationErrors errors = validator.validate(Request.post().json().path("/star-json").body("{}"));
        assertFalse(errors.isEmpty());
    }

    @Test
    void typeStarTest() {
        ValidationErrors errors = validator.validate(Request.post().json().path("/application-star").body("{}"));
        assertTrue(errors.isEmpty());
    }
}
