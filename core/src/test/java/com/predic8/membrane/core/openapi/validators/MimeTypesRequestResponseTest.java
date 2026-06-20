/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import jakarta.mail.internet.ParseException;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MimeTypesRequestResponseTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/mimetypes.yml";
    }

    // application/x-www-form-urlencoded is still not implemented
    @Test
    public void formUrlEncodedResponseNotImplemented() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/mimetypes"),
                Response.statusCode(202).mediaType(APPLICATION_X_WWW_FORM_URLENCODED).body("{ }"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("not implemented"));
    }

    @Test
    public void formUrlEncodedRequestNotImplemented() throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/x-www-form-urlencoded").mediaType(APPLICATION_X_WWW_FORM_URLENCODED).body("name=Alice"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("not implemented"));
    }

    // XML: invalid XML produces a parse error (not "not implemented" any more)
    @Test
    public void invalidXmlRequestBodyProducesParseError() throws ParseException {
        ValidationErrors errors = validator.validate(
                Request.post().path("/application-xml").mediaType(APPLICATION_XML).body("{ not-xml }"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("cannot be parsed as xml"));
    }

    @Test
    public void invalidXmlResponseBodyProducesParseError() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/mimetypes"),
                Response.statusCode(200).mediaType(APPLICATION_XML).body("{ not-xml }"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("cannot be parsed as xml"));
    }
}
