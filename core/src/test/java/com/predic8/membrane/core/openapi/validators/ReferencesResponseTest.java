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


public class ReferencesResponseTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/references-response.yml";
    }

    @Test
    public void refRequestOk()  {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"),Response.statusCode(200)
                .json().body(getResourceAsStream("/openapi/messages/references-requests-responses-customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void refResponseInvalid() throws ParseException {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/ref-response"), Response.statusCode(200).mediaType(APPLICATION_JSON).body(getResourceAsStream("/openapi/messages/references-requests-responses-customer-invalid.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(ValidationContext.ValidatedEntityType.BODY,e.getContext().getValidatedEntityType());
        assertEquals("RESPONSE",e.getContext().getValidatedEntity());
        assertEquals("string",e.getContext().getSchemaType());
        assertEquals(500,e.getContext().getStatusCode());
        assertEquals("RESPONSE/BODY#/name", e.getContext().getLocationForResponse());
    }
}