package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
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
        assertFalse(new ContentType("*/*").match(APPLICATION_JSON));
    }

    @Test
    void contentTypeMatchingSwitch() throws ParseException {
        assertTrue(new ContentType(APPLICATION_JSON).match("application/*"));
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

    @Test
    void starStarResponseTest() throws ParseException {
        ValidationErrors errors = validator.validateResponse(
                Request.post().json().path("/application-star").body("{}"),
                Response.statusCode(200).mediaType(APPLICATION_JSON).body(getResourceAsStream("/openapi/messages/customer.json")));
        assertTrue(errors.isEmpty());
    }

    @Test
    void starTypeResponseTest() throws ParseException {
        ValidationErrors errors = validator.validateResponse(
                Request.post().json().path("/star-json").body("{}"),
                Response.statusCode(200).mediaType(APPLICATION_JSON).body(getResourceAsStream("/openapi/messages/customer.json")));
        assertFalse(errors.isEmpty());
    }

    @Test
    void typeStarResponseTest() throws ParseException {
        ValidationErrors errors = validator.validateResponse(
                Request.post().json().path("/star-star").body("{}"),
                Response.statusCode(200).mediaType(APPLICATION_JSON).body(getResourceAsStream("/openapi/messages/customer.json")));
        assertTrue(errors.isEmpty());
    }
}
