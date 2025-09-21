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