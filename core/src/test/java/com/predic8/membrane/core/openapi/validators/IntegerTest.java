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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.predic8.membrane.core.openapi.model.*;
import org.junit.jupiter.api.*;

import java.math.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


public class IntegerTest extends AbstractValidatorTest {

    @Override
protected String getOpenAPIFileName() {
        return "/openapi/specs/integer.yml";
    }

    @Test
    public void validMinimumInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/integer?minimum=3"));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertEquals(QUERY_PARAMETER, e.getContext().getValidatedEntityType());
        assertTrue(e.getMessage().contains("minimum"));
        assertEquals("REQUEST/QUERY_PARAMETER/minimum", e.getContext().getLocationForRequest());
    }

    @Test
    public void validMaximumInQuery() {
        ValidationErrors errors = validator.validate(Request.get().path("/integer?maximum=13"));
        assertEquals(1,errors.size());
    }

    @Test
    public void validMinimumInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("minimum",new BigDecimal(7)))));
        assertEquals(0,errors.size());
    }

    @Test
    public void validMinimumInBodyExact() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("minimum",new BigDecimal(5)))));
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidMinimumInBodyExactExclusive() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("exclusiveMinimum",new BigDecimal(5)))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("exclusive"));
    }

    @Test
    public void invalidMinimumInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("minimum",new BigDecimal(3)))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("minimum"));
    }

    @Test
    public void validMaximumInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("maximum",new BigDecimal(3)))));
        assertEquals(0,errors.size());
    }

    @Test
    public void validMaximumInBodyExact() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("maximum",new BigDecimal(5)))));
        assertEquals(0,errors.size());
    }

    @Test
    public void invalidMaximumInBodyExactExclusive() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("exclusiveMaximum",new BigDecimal(5)))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("exclusive"));
    }

    @Test
    public void invalidMaximumInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("maximum",new BigDecimal(13)))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("maximum"));
    }

    @Test
    public void invalidMaximumWithoutTypeInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("maximum-without-type",new BigDecimal(13)))));
        assertEquals(1,errors.size());
        ValidationError e = errors.get(0);
        assertTrue(e.getMessage().contains("maximum"));
    }

    @Test
    public void validMaximumWithoutTypeInBody() {
        ValidationErrors errors = validator.validate(Request.post().path("/integer").body(new JsonBody(getNumbers("maximum-without-type",new BigDecimal(5)))));
        assertEquals(0,errors.size());
    }

    private JsonNode getNumbers(String name, BigDecimal n) {
        ObjectNode root = om.createObjectNode();
        root.put(name,n);
        return root;
    }
}