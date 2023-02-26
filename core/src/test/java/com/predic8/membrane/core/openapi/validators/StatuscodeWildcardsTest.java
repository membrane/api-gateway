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

import static com.predic8.membrane.core.openapi.model.Response.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static org.junit.jupiter.api.Assertions.*;


public class StatuscodeWildcardsTest extends AbstractValidatorTest {

    Request two = Request.get().path("/two");

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/statuscode-wildcards.yml";
    }

    @Test
    void specificStatuscode200() {
        assertTrue(validator.validateResponse(two,  statusCode(200).json().body("42")).isEmpty());
    }

    @Test
    void specificStatuscode307() {
        assertTrue(validator.validateResponse(two,  statusCode(307).json().body("[]")).isEmpty());
    }

    @Test
    void specificStatuscodeInvalid() {
        ValidationErrors e = validator.validateResponse(two, statusCode(200).json().body("true"));
        System.out.println("e = " + e);
        assertFalse(e.isEmpty());
    }

    @Test
    void wildcardShouldMatch200() {
        assertTrue(validator.validateResponse(two, statusCode(250).json().body("true")).isEmpty());
    }

    @Test
    void wildcardShouldMatch300() {
        assertTrue(validator.validateResponse(two, statusCode(301).json().body("{}")).isEmpty());
    }

    @Test
    void wildcardShouldMatchInvalid() {
        ValidationErrors e = validator.validateResponse(two, statusCode(208).json().body("{}"));
        assertEquals(1,e.size());
        ValidationContext ctx = e.get(0).getContext();
        assertEquals(500, ctx.getStatusCode());
        assertEquals("boolean", ctx.getSchemaType());
        assertEquals(BODY, ctx.getValidatedEntityType());
    }

}