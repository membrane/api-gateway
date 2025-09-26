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