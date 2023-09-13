package com.predic8.membrane.core.interceptor.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaddingHeaderInterceptorTest {

    private PaddingHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PaddingHeaderInterceptor(5, 7, 10);
    }

    @Test
    void testGenerateLookupTableLength() {
        char[] lookupTable = PaddingHeaderInterceptor.generateLookupTable();
        assertEquals(62, lookupTable.length);
    }

    @Test
    void testHttpCryptoSafePaddingLength() {
        String padding = interceptor.httpCryptoSafePadding(10);
        assertEquals(10, padding.length());
    }

    @Test
    void testHttpCryptoSafePaddingCharacters() {
        String padding = interceptor.httpCryptoSafePadding(100);
        for (char c : padding.toCharArray()) {
            assertTrue(Character.isLetterOrDigit(c));
            assertFalse(Character.isWhitespace(c));
        }
    }
}

