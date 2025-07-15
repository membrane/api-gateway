package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.cors.CorsUtil.normalizeOrigin;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.splitBySpace;
import static org.junit.jupiter.api.Assertions.*;

class CorsUtilTest {

    @Test
    void testNormalizeOrigin() {
        assertEquals("/foo/bar", normalizeOrigin("/foo/bar//"));
        assertEquals("bar", normalizeOrigin("bar"));
        assertEquals("", normalizeOrigin(""));
        assertEquals("", normalizeOrigin("/"));
        assertEquals("", normalizeOrigin("//"));
        assertEquals("/foo", normalizeOrigin("/foo/"));

        assertEquals("http://example.com", normalizeOrigin("HTTP://EXAMPLE.COM"));
        assertEquals("https://api.test.com/path", normalizeOrigin("HTTPS://API.TEST.COM/PATH/"));
    }


    @Test
    void splitStringBySpace() {
        assertEquals(Set.of("a", "b", "c"), splitBySpace(" a a  b   c "));
        assertEquals(Set.of(), splitBySpace(""));
        assertEquals(Set.of(), splitBySpace("   "));
        assertEquals(Set.of("single"), splitBySpace("single"));
        assertEquals(Set.of("a"), splitBySpace("a a a")); // duplicates handled
    }
}