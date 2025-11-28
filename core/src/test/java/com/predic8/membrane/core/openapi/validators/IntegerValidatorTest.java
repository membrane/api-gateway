/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.validators;

import tools.jackson.databind.node.*;
import org.junit.jupiter.api.*;

class IntegerValidatorTest {

    static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    IntegerValidator validator;
    ValidationContext ctx = ValidationContext.create();

    @BeforeEach
    void setUp() {
        validator = new IntegerValidator();
    }

    @Test
    void string() {
        validator.validate(ctx,"1");
    }

    @Test
    void integer() {
        validator.validate(ctx,1);
    }

    @Test
    void number() {
        validator.validate(ctx,FACTORY.numberNode(1));
    }

    @Test
    void numberLong() {
        validator.validate(ctx,FACTORY.numberNode(111111111111111111L));
    }

    @Test
    void invalid() {
        validator.validate(ctx,"fff");
    }
}