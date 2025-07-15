package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class PreflightHandlerTest {

    private PreflightHandler ph;

    @BeforeEach
    void beforeAll() {
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

        assertTrue(ph.headersAllowed("Accept")); // case insensitive
        assertTrue(ph.headersAllowed("  accept  ")); // whitespace handling
        assertFalse(ph.headersAllowed("x-custom-header")); // custom headers
        assertFalse(ph.headersAllowed("authorization")); // sensitive header
        assertTrue(ph.headersAllowed("Accept,content-Type")); // multiple safe headers
        assertFalse(ph.headersAllowed("accept,custom-header")); // mixed safe/unsafe
    }
}