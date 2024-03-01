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
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.JsonUtil.*;
import static org.junit.jupiter.api.Assertions.*;


public class NullableTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/nullable.yml";
    }

    @Test
    public void emailNullValid() {

        Map<String,Object> m = new HashMap<>();
        m.put("email",null);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void addressObjectNullValid() {

        Map<String,Object> m = new HashMap<>();
        m.put("address",null);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void contactNullableWithoutTypeInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("contact",null);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
//        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/contact", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().contains("null"));
    }

    @Test
    public void telefonNotNullableInvalid() {

        Map<String,Object> m = new HashMap<>();
        m.put("telefon",null);

        ValidationErrors errors = validator.validate(Request.post().path("/composition").body(mapToJson(m)));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals("/telefon", e.getContext().getJSONpointer());
        assertTrue(e.getMessage().toLowerCase().contains("null"));
    }
}