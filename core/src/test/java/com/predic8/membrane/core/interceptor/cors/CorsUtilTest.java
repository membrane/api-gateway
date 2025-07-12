package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class CorsUtilTest {

    @Test
    void removeTrailing() {
        assertEquals("/foo/bar",CorsUtil.removeTrailingSlashes("/foo/bar//"));
        assertEquals("bar",CorsUtil.removeTrailingSlashes("bar"));
    }
}