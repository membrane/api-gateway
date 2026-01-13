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

package com.predic8.membrane.core.openapi.oas31.parameters;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.model.Request.*;
import static org.junit.jupiter.api.Assertions.*;

public class ArrayExplodeOAS31Test extends AbstractArrayExplodeOASXXTest {

    @Override
    protected String getOpenAPIFileName() {
        return "/openapi/specs/oas31/parameters/array-explode-3.1.X.yaml";
    }

    /**
     * OpenAPI parser delivers different values for schema.type for array in 3.0.X and 3.1.X
     */
    @Test
    void numbersInvalid() {
        var err = validator.validate(get().path("/foo?numbers=1&numbers=notANumber"));
        assertEquals(1, err.size());
        assertTrue(err.get(0).getMessage().contains("does not match any of [number]"));
    }
}
