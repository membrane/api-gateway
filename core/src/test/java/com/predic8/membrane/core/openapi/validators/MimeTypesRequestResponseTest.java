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

import com.predic8.membrane.core.openapi.model.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class MimeTypesRequestResponseTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/mimetypes.yml";
    }

    @Test
    public void notImplementedResponse() throws ParseException {
        testNotImplementedResponse(200, APPLICATION_XML);
        testNotImplementedResponse(201, TEXT_XML);
        testNotImplementedResponse(202, APPLICATION_X_WWW_FORM_URLENCODED);
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
        testNotImplementedRequest(APPLICATION_XML,"/application-xml");
        testNotImplementedRequest(TEXT_XML, "/text-xml");
        testNotImplementedRequest(APPLICATION_X_WWW_FORM_URLENCODED, "/x-www-form-urlencoded");
    }

    private void testNotImplementedRequest(String mimeType, String path) throws ParseException {
        ValidationErrors errors = validator.validate(Request.post().path(path).mediaType(mimeType).body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().toLowerCase().contains("not implemented"));
    }
}
