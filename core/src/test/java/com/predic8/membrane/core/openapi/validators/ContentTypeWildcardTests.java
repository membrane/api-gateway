/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

public class ContentTypeWildcardTests extends AbstractValidatorTest {


    private static final Request STAR_STAR = Request.post().json().path("/star-star").body("{}");
    private static final Request STAR_JSON = Request.post().json().path("/star-json").body("{}");
    private static final Request APPLICATION_STAR = Request.post().json().path("/application-star").body("{}");

    @Override
protected String getOpenAPIFileName() {
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
        validateAndAssert(STAR_STAR, true);
    }

    @Test
    void starTypeTest() {
        validateAndAssert(STAR_JSON, false);
    }

    @Test
    void typeStarTest() {
        validateAndAssert(APPLICATION_STAR, true);
    }

    void responseTest(Request request, boolean expectedResult) {
        ValidationErrors errors = validator.validateResponse(
                request,
                Response.statusCode(200).json().body("{}"));
        assertEquals(expectedResult, errors.isEmpty());
    }

    @Test
    void starStarResponseTest() throws ParseException {
        responseTest(STAR_STAR, true);
    }

    @Test
    void starTypeResponseTest() throws ParseException {
        responseTest(STAR_JSON, false);
    }

    @Test
    void typeStarResponseTest() throws ParseException {
        responseTest(APPLICATION_STAR, true);
    }
}
