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

import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.JsonUtil.mapToJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class NullTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/null.yml";
    }

    @Test
    public void nullValid() {
        ValidationErrors errors = validator.validate(post().path("/null-type").body(mapToJson(null)));
        assertEquals(0,errors.size());
    }

    @Test
    public void nullInvalid() {
        ValidationErrors errors = validator.validate(post().path("/null-type").body(mapToJson("foo")));
        assertEquals(1,errors.size());
        assertTrue(errors.get(0).getMessage().contains("\"foo\" is of type string"));
    }

    @Test
    public void nullOrIntegerArrayValid() {
        List<?> l = Arrays.asList(new Object[] {null, 1});
        ValidationErrors errors = validator.validate(post().path("/array-null-or-integer-type").body(mapToJson(l)));
        assertEquals(0,errors.size());
    }

    @Test
    public void nullOrIntegerArrayInvalid() {
        List<?> l = Arrays.asList(new Object[] {null, "foo"});
        ValidationErrors errors = validator.validate(post().path("/array-null-or-integer-type").body(mapToJson(l)));
        assertEquals(1,errors.size());
        assertTrue(errors.get(0).getMessage().contains("\"foo\" is of type string"));
    }

}