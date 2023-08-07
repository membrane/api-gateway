/*
 *  Copyright 2023 predic8 GmbH, www.predic8.com
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

import static org.junit.jupiter.api.Assertions.*;


public class EdgeCasesTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/edge-cases.yml";
    }


    @Test
    void noContent() {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/no-content"), Response.statusCode(200).json().body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(1,errors.size());
        ValidationError error = errors.get(0);
        assertTrue(error.getMessage().contains("Response shouldn't have a body"));
    }

    @Test
    void contentNoSchema() {
        ValidationErrors errors = validator.validateResponse(Request.get().path("/content-no-schema"), Response.statusCode(200).json().body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void contentMediatypeNoSchema(){
        ValidationErrors errors = validator.validateResponse(Request.get().path("/content-mediatype-no-schema"), Response.statusCode(200).json().body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    void contentEmptySchema(){
        ValidationErrors errors = validator.validateResponse(Request.get().path("/content-empty-schema"), Response.statusCode(200).json().body("{}"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }
}