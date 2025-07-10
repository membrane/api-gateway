package com.predic8.membrane.core.interceptor.cors;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.cors.CorsUtil.normalizeOrigin;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.parseCommaOrSpaceSeparated;
import static org.junit.jupiter.api.Assertions.*;

class CorsUtilTest {

    @Test
    void normalize() {
        assertEquals("https://predic8.de", normalizeOrigin("https://predic8.de"));
        assertEquals("https://predic8.de", normalizeOrigin("https://predic8.de/"));
        assertEquals("https://predic8.de", normalizeOrigin("https://Predic8.de"));
    }

    @Test
    void testParseCommaSeparated() {
        assertEquals(Set.of("a","b","c"), parseCommaOrSpaceSeparated("a b c"));
        assertEquals(Set.of("a","b"), parseCommaOrSpaceSeparated("a,b"));
        assertEquals(Set.of("a","b"), parseCommaOrSpaceSeparated("a,b"));
        assertEquals(Set.of("a","b"), parseCommaOrSpaceSeparated("a, b"));
        assertEquals(Set.of("a","b"), parseCommaOrSpaceSeparated("a ,b"));
        assertEquals(Set.of("a","b"), parseCommaOrSpaceSeparated(" a ,,,,b "));
    }

}