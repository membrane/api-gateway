package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeaderNameTest {

    HeaderName hn = new HeaderName("Test-Header");

    @Test
    void testEquals() {
        assertEquals(hn, new HeaderName("TEST-HEADER"));
    }

    @Test
    void hasName() {
        assertTrue(hn.hasName("test-header"));
    }

    @Test
    void testHashCode() {
        assertEquals(hn.hashCode(), new HeaderName("test-Header").hashCode());
    }

    @Test
    void testToString() {
        assertEquals("Test-Header", hn.toString());
    }
}