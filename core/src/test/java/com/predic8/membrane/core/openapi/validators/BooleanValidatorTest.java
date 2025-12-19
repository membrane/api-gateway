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

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.BOOLEAN;
import static org.junit.jupiter.api.Assertions.*;

class BooleanValidatorTest {

    BooleanValidator validator = new BooleanValidator();

    @Test
    void can_validate() {
        assertEquals(BOOLEAN,validator.canValidate("true"));
        assertEquals(BOOLEAN,validator.canValidate("false"));
        assertEquals(BOOLEAN,validator.canValidate("NO"));
        assertEquals(BOOLEAN,validator.canValidate("yes"));
        assertNull(validator.canValidate(null));
    }

    @Test
    void do_validation() {
        assertNull(validator.validate(null,"Yes"));
        assertNull(validator.validate(null,"no"));
        assertNull(validator.validate(null,"true"));
        assertNull(validator.validate(null,"false"));
    }

}