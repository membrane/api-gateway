package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class MimeTypesRequestResponseTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/mimetypes.yml";
    }

    @Test
    public void notImplementedResponse() throws ParseException {
        testNotImplementedResponse(200, "application/xml");
        testNotImplementedResponse(201, "text/xml");
        testNotImplementedResponse(202, "application/x-www-form-urlencoded");
    }

    private void testNotImplementedResponse(int statusCode, String mimeType) throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/mimetypes"),
                Response.statusCode(statusCode).mediaType(mimeType).body("{ }"));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().toLowerCase().contains("not implemented"));
    }

    @Test
    public void notImplementedRequest() throws ParseException {
        testNotImplementedRequest("application/xml","/application-xml");
        testNotImplementedRequest("text/xml", "/text-xml");
        testNotImplementedRequest("application/x-www-form-urlencoded", "/x-www-form-urlencoded");
    }

    private void testNotImplementedRequest(String mimeType) throws ParseException {
        testNotImplementedRequest(mimeType,"/mimetypes" );
    }

    private void testNotImplementedRequest(String mimeType, String path) throws ParseException {
        ValidationErrors errors = validator.validate(Request.post().path(path).mediaType(mimeType).body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().toLowerCase().contains("not implemented"));
    }
}
