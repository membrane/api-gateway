package com.predic8.membrane.core.interceptor.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.security.PaddingHeaderInterceptor.LOOKUP_TABLE;
import static org.junit.jupiter.api.Assertions.*;

class PaddingHeaderInterceptorTest {

    public static final String HTTP_HEADER_SPECIAL_CHARS = " _:;.,\\/\"'?!(){}[]@<>=-+*#$&`|~^%";
    private PaddingHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new PaddingHeaderInterceptor(5, 7, 10);
    }

    @Test
    void testLookupTableLength() {
        assertEquals(95, LOOKUP_TABLE.length());
    }

    @Test
    void testLookupTableCompleteness() {
            char[] chars = new char[72];
            int index = 0;
            for (char c = 'a'; c <= 'z'; c++) {
                chars[index++] = c;
            }
            for (char c = 'A'; c <= 'Z'; c++) {
                chars[index++] = c;
            }
            for (char c = '0'; c <= '9'; c++) {
                chars[index++] = c;
            }
            String headerValueSafeChars = new String(chars).trim() + HTTP_HEADER_SPECIAL_CHARS;
            assertEquals(headerValueSafeChars, LOOKUP_TABLE);
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

    private void testHeaderSafePadding() {
        assertEquals(10, interceptor.headerSafePadding(10).length());
    }
}

