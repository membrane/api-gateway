package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

public class ContentTypeWildcardTests extends AbstractValidatorTest {

    private static final String STAR_STAR_PATH = "/star-star";
    private static final String STAR_JSON_PATH = "/star-json";
    private static final String APPLICATION_STAR_PATH = "/application-star";

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

    void validateAndAssert(Request request, boolean expectedResult) {
        ValidationErrors errors = validator.validate(request);
        assertEquals(expectedResult, errors.isEmpty());
    }

    @Test
    void starStarTest() {
        validateAndAssert(Request.post().json().path(STAR_STAR_PATH).body("{}"), true);
    }

    @Test
    void starTypeTest() {
        validateAndAssert(Request.post().json().path(STAR_JSON_PATH).body("{}"), false);
    }

    @Test
    void typeStarTest() {
        validateAndAssert(Request.post().json().path(APPLICATION_STAR_PATH).body("{}"), true);
    }

    void responseTest(Request request, boolean expectedResult) throws ParseException {
        ValidationErrors errors = validator.validateResponse(
                request,
                Response.statusCode(200).json().body("{}"));
        assertEquals(expectedResult, errors.isEmpty());
    }

    @Test
    void starStarResponseTest() throws ParseException {
        responseTest(Request.post().json().path(STAR_STAR_PATH).body("{}"), true);
    }

    @Test
    void starTypeResponseTest() throws ParseException {
        responseTest(Request.post().json().path(STAR_JSON_PATH).body("{}"), false);
    }

    @Test
    void typeStarResponseTest() throws ParseException {
        responseTest(Request.post().json().path(APPLICATION_STAR_PATH).body("{}"), true);
    }
}
