package com.predic8.membrane.core.interceptor.templating;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticInterceptorTest {

    @Test
    void testTrimIndent() {
        assertEquals("  line1\nline2\n  line3", StaticInterceptor.trimIndent("    line1\n  line2\n    line3"));
        assertEquals("line1\nline2\nline3", StaticInterceptor.trimIndent("line1\nline2\nline3"));
        assertEquals("line1\nline2\nline3", StaticInterceptor.trimIndent("    line1\n    line2\n    line3"));
        assertEquals("line1\n\nline3", StaticInterceptor.trimIndent("    line1\n\n    line3"));
        assertEquals("\nline1\nline2\nline3", StaticInterceptor.trimIndent("\n    line1\n    line2\n    line3\n"));
        assertEquals("", StaticInterceptor.trimIndent("\n\n\n"));

    }
}