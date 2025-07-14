package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.cors.CorsUtil.splitBySpace;
import static org.junit.jupiter.api.Assertions.*;

class CorsUtilTest {

    @Test
    void removeTrailing() {
        assertEquals("/foo/bar",CorsUtil.removeTrailingSlashes("/foo/bar//"));
        assertEquals("bar",CorsUtil.removeTrailingSlashes("bar"));
    }

    @Test
    void splitStringBySpace() {
        assertEquals(Set.of("a","b","c"), splitBySpace(" a a  b   c "));
    }
}