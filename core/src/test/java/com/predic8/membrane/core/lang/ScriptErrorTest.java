/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.lang;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScriptErrorTest {

    @Test
    void validLocation() {
        final var error = new ScriptError(true, 3, 5, Map.of());
        assertEquals(3, error.line());
        assertEquals(5, error.column());
    }

    @Test
    void lineBeforeScriptIsUnknown() {
        final var error = new ScriptError(true, -7, 5, Map.of());
        assertNull(error.line());
        assertNull(error.column());
    }

    @Test
    void invalidColumnIsUnknown() {
        final var error = new ScriptError(true, 3, 0, Map.of());
        assertEquals(3, error.line());
        assertNull(error.column());
    }
}
