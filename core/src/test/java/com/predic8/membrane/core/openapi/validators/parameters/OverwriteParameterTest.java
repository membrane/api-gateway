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

package com.predic8.membrane.core.openapi.validators.parameters;

import com.predic8.membrane.core.openapi.validators.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static org.junit.jupiter.api.Assertions.*;

public class OverwriteParameterTest extends AbstractValidatorTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/overwrite.yaml";
    }

    @Test
    void operationParameterOverwritesPathParameter() {
        ValidationErrors err = validator.validate(get().path("/overwrite?a=foo"));
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("of [number]"));
    }

    @Test
    void headerParameterDoesNotInterfere() {
        ValidationErrors err = validator.validate(get().path("/overwrite?b=foo"));
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("of [boolean]"));
    }
}