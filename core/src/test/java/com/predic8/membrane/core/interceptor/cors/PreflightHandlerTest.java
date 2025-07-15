package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PreflightHandlerTest {

    static PreflightHandler ph;

    @BeforeAll
    static void beforeAll() {
        CorsInterceptor ci = new CorsInterceptor();
        ph = new PreflightHandler(ci);
    }

    @Test
    void headersAllowed() {
        assertTrue(ph.headersAllowed(   ""));
        assertTrue(ph.headersAllowed(   null));

        // fetch safe headers
        assertTrue(ph.headersAllowed(   "accept, accept-language, content-language, content-type, range"));

        assertFalse(ph.headersAllowed(   "Foo"));
    }
}