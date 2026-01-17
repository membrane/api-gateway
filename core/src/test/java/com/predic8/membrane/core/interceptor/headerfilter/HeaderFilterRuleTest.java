package com.predic8.membrane.core.interceptor.headerfilter;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterInterceptor.Action.KEEP;
import static com.predic8.membrane.core.interceptor.headerfilter.HeaderFilterRule.*;
import static org.junit.jupiter.api.Assertions.*;

class HeaderFilterRuleTest {

    @Test
    void matchAll() {
        assertTrue(  remove(".*").matches("Foo"));
    }

    @Test
    void matchPattern() {
        var hfr = HeaderFilterRule.keep("X-[F|Z]oo");
        assertTrue(hfr.matches("X-Foo"));
        assertTrue(hfr.matches("X-Zoo"));
        assertFalse(hfr.matches("X-Boo"));
        assertEquals(KEEP, hfr.getAction());
    }

    @Test
    void caseInsensitive() {
        var hfr = HeaderFilterRule.keep("x-bar.*");
        assertTrue(hfr.matches("x-bar"));
        assertTrue(hfr.matches("X-Bar"));
        assertTrue(hfr.matches("X-BARRRR"));
        assertTrue(hfr.matches("x-Barrrrr"));
    }

}