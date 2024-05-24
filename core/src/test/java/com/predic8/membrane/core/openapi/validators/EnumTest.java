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
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


class EnumTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/enum.yml";
    }

    @Test
    void enumValid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","amber");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
        assertEquals(0,errors.size());
    }

    @Test
    void enumInvalid() {

        Map<String,String> m = new HashMap<>();
        m.put("state","blue");

        ValidationErrors errors = validator.validate(Request.post().path("/enum").body(mapToJson(m)));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(BODY, e.getContext().getValidatedEntityType());
        assertEquals("REQUEST", e.getContext().getValidatedEntity());
        assertTrue(e.getMessage().contains("enum"));
        assertEquals("REQUEST/BODY#/state", e.getContext().getLocationForRequest());
    }

    /*

    Enum without type is not possible with openapi parser. The parser assumes string!
    https://json-schema.org/understanding-json-schema/reference/generic.html

     */
//    @Test
//    public void readOnlyValid() {
//
//        Map m = new HashMap();
//        m.put("state",42);
//
//        ValidationErrors errors = validator.validate(Request.post().path("/enum-without-type").body(mapToJson(m)));
//        assertEquals(0,errors.size());
//    }
}