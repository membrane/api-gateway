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

import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.TestUtils.getResourceAsStream;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

public class RequestsTest {

    OpenAPIValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new OpenAPIValidator(getResourceAsStream(this, "/openapi/specs/customers.yml"));
    }

    @Test
    public void wrongMediaTypeRequest() {

        ValidationErrors errors = validator.validate(Request.post().path("/customers").mediaType("text/plain").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationContext ctx = errors.get(0).getContext();
        assertEquals(MEDIA_TYPE,ctx.getValidatedEntityType());
        assertEquals("text/plain",ctx.getValidatedEntity());
        assertEquals(415,ctx.getStatusCode());
        assertTrue(errors.get(0).getMessage().toLowerCase().contains("mediatype"));
        assertEquals("REQUEST/HEADER/Content-Type", ctx.getLocationForRequest());
    }

    /**
     * The OpenAPI does not specify a content for a request, but payload is sent.
     */
    @Test
    public void noContentInRequestSentPayload() {

        ValidationErrors errors = validator.validate(Request.get().path("/customers").mediaType("application/json").body(getResourceAsStream(this, "/openapi/messages/customer.json")));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY,e.getContext().getValidatedEntityType());
        assertEquals("REQUEST",e.getContext().getValidatedEntity());
        assertEquals(400,e.getContext().getStatusCode());
        assertTrue(e.getMessage().toLowerCase().contains("body"));
        assertEquals("REQUEST/BODY", e.getContext().getLocationForRequest());
    }
}