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

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.jupiter.api.Assertions.*;


public class ReadWriteOnlyTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/read-write-only.yml";
    }

    @Test
    public void readOnlyValid() {

        Map<String,String> m = new HashMap<>();
        m.put("name","Jack");

        ValidationErrors errors = validator.validate(Request.put().path("/read-only").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void readOnlyInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("id",7);
        m.put("name","Jack");

        ValidationErrors errors = validator.validate(Request.put().path("/read-only").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/id",e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("7"));
        assertEquals("REQUEST/BODY#/id", e.getContext().getLocationForRequest());
    }

    @Test
    public void writeOnlyInvalid() throws ParseException {

        Map<String,Object> m = new HashMap<>();
        m.put("id",7);
        m.put("name","Jack");
        m.put("role","admin");

        ValidationErrors errors = validator.validateResponse(Request.get().path("/read-only"), Response.statusCode(200).mediaType(APPLICATION_JSON).body(mapToJson(m)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
//        System.out.println("errors = " + errors);
        assertEquals("/role",e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("admin"));
        assertEquals("REQUEST/BODY#/role", e.getContext().getLocationForRequest());
    }
}