package com.predic8.membrane.core.openapi.validators.parameters;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;

import java.math.*;

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
    void parseLong() {
        JsonNode node = asJson("12345678901233333");
        assertTrue(node.isBigInteger() );
        assertEquals(new BigInteger("12345678901233333"), node.bigIntegerValue());
    }

    @Test
    void parseDecimal() {
        JsonNode node = asJson("3.14");
        assertTrue(node.isBigDecimal());
        assertEquals(3.14, node.doubleValue(), 0.0001);
    }

    @Test
    void scientific() {
        JsonNode node = asJson("3e4");
        assertTrue(node.isBigDecimal());
        assertEquals(3e4, node.doubleValue(), 0.0001);
    }

    @Test
    void text() {
        JsonNode node = asJson("foo");
        assertTrue(node.isTextual());
        assertEquals("foo", node.textValue());
    }

    @Test
    void explicitNull() {
        JsonNode node = asJson("null");
        assertTrue(node.isNull());
    }

    @Test
    void emptyString() {
        JsonNode node = asJson("");
        assertTrue(node.isTextual());
        assertEquals("", node.textValue());
    }


}