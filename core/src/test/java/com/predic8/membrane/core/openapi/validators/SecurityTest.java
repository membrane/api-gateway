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

import static org.junit.jupiter.api.Assertions.*;


public class SecurityTest extends AbstractValidatorTest {

    @Override
    String getOpenAPIFileName() {
        return "/openapi/specs/oauth2.yml";
    }

    @Test
    public void simple() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }

    @Test
    public void two() {
        ValidationErrors errors = validator.validate(Request.get().path("/v1/finance-and-write"));
        System.out.println("errors = " + errors);
        assertEquals(0,errors.size());
    }



}