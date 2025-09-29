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

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.openapi.validators.parameters.AbstractParameter.asJson;
import static org.junit.jupiter.api.Assertions.*;

class AbstractParameterTest {
    @Test
    void booleanTrue() {
        JsonNode node = asJson("true");
        assertTrue(node.isBoolean());
        assertTrue(node.booleanValue());
    }

    @Test
    void booleanFalse() {
        JsonNode node = asJson("false");
        assertTrue(node.isBoolean());
        assertFalse(node.booleanValue());
    }

    @Test
    void integer() {
        JsonNode node = asJson("42");
        assertTrue(node.isInt());
        assertEquals(42, node.intValue());
    }

    @Test
    void testLong() {
        JsonNode node = asJson("1234567890123");
        assertTrue(node.isLong());
        assertEquals(1234567890123L, node.longValue());
    }

    @Test
    void testDouble() {
        JsonNode node = asJson("3.14");
        assertTrue(node.isDouble());
        assertEquals(3.14, node.doubleValue(), 0.0001);
    }

    @Test
    void text() {
        JsonNode node = asJson("foo");
        assertTrue(node.isTextual());
        assertEquals("foo", node.textValue());
    }
}