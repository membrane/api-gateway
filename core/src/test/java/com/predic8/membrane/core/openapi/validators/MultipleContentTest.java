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

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultipleContentTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/multiple-content.yml";
    }

    @Test
    public void returnJSON() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/foo"), Response.statusCode(200).mediaType(APPLICATION_JSON).body("""
                {
                    "name": "Alice"
                }
                """));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void returnXML() throws ParseException {

        ValidationErrors errors = validator.validateResponse(Request.get().path("/foo"), Response.statusCode(200).mediaType(APPLICATION_JSON).body("""
                {
                    "name": "Alice"
                }
                """));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }


}