package com.predic8.membrane.core.interceptor.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.security.PaddingHeaderInterceptor.generateLookupTable;
import static java.lang.Character.isLetterOrDigit;
import static java.lang.Character.isWhitespace;
import static org.junit.jupiter.api.Assertions.*;

class PaddingHeaderInterceptorTest {

    private PaddingHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PaddingHeaderInterceptor(5, 7, 10);
    }

    @Test
    void testGenerateLookupTableLength() {
        assertEquals(62, generateLookupTable().length);
    }

    @Test
    void testHttpCryptoSafePaddingLength() {
        assertEquals(10, interceptor.httpCryptoSafePadding(10).length());
    }

    @Test
    void testHttpCryptoSafePaddingCharacters() {
        char[] padding = interceptor.httpCryptoSafePadding(100).toCharArray();
        assertEquals(100, padding.length);
        for (char c : padding) {
            assertTrue(isLetterOrDigit(c));
            assertFalse(isWhitespace(c));
        }
    }

    @Test
    void calculatePaddingSize() {
        for(int i=0;i<1_000;i++){
            assertTrue(interceptor.calculatePaddingSize(i) < 33);
        }
    }

    @Test
    void roundUp() {
        assertEquals(2, interceptor.roundUp(3));
        assertEquals(3, interceptor.roundUp(2));
        assertEquals(5, interceptor.roundUp(15));
        assertEquals(5, interceptor.roundUp(5));
        assertEquals(5, interceptor.roundUp(0));
    }

    @Test
    void getRandomZeroUpTo() {
        for(int i=0;i<1_000;i++){
            assertTrue(interceptor.getRandomNumber() < 10);
            assertTrue(interceptor.getRandomNumber() >= 0);
        }
    }
}

