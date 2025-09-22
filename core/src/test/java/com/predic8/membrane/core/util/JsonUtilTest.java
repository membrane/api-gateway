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

package com.predic8.membrane.core.util;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.JsonUtil.scalarAsJson;
import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void booleanTrue() {
        JsonNode node = scalarAsJson("true");
        assertTrue(node.isBoolean());
        assertTrue(node.booleanValue());
    }

    @Test
    void booleanFalse() {
        JsonNode node = scalarAsJson("false");
        assertTrue(node.isBoolean());
        assertFalse(node.booleanValue());
    }

    @Test
    void integer() {
        JsonNode node = scalarAsJson("42");
        assertTrue(node.isInt());
        assertEquals(42, node.intValue());
    }

    @Test
    void parseLong() {
        JsonNode node = scalarAsJson("12345678901233333");
        assertTrue(node.isLong() );
        assertEquals(12345678901233333L, node.longValue());
    }

    @Test
    void parseDecimal() {
        JsonNode node = scalarAsJson("3.14");
        assertTrue(node.isBigDecimal());
        assertEquals(0, node.decimalValue().compareTo(new java.math.BigDecimal("3.14")));
    }

    @Test
    void scientific() {
        JsonNode node = scalarAsJson("3e4");
        assertTrue(node.isBigDecimal());
        assertEquals(0, node.decimalValue().compareTo(new java.math.BigDecimal("3e4")));
    }

    @Test
    void text() {
        JsonNode node = scalarAsJson("foo");
        assertTrue(node.isTextual());
        assertEquals("foo", node.textValue());
    }

    @Test
    void explicitNull() {
        JsonNode node = scalarAsJson("null");
        assertTrue(node.isNull());
    }

    @Test
    void emptyString() {
        JsonNode node = scalarAsJson("");
        assertTrue(node.isTextual());
        assertEquals("", node.textValue());
    }

}