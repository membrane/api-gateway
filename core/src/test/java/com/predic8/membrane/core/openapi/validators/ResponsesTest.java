/*
 *  Copyright 2024 predic8 GmbH, www.predic8.com
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
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

public class ResponsesTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/customers.yml";
    }

    @Test
    public void wrongMediaTypeRequest() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.put().path("/customers").mediaType(TEXT_PLAIN).body(getResourceAsStream("/openapi/messages/customer.json")),
                Response.statusCode(200).body("foo"));
        assertEquals(1,errors.size());
        ValidationContext ctx = errors.get(0).getContext();
        assertEquals(BODY,ctx.getValidatedEntityType());
    }


}