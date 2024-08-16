package com.predic8.membrane.core.interceptor.templating;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.templating.StaticInterceptor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticInterceptorTest {

    @Test
    void testTrimIndent() {
        assertEquals("  line1\nline2\n  line3", trimIndent("    line1\n  line2\n    line3"));
        assertEquals("line1\nline2\nline3", trimIndent("line1\nline2\nline3"));
        assertEquals("line1\nline2\nline3", trimIndent("    line1\n    line2\n    line3"));
        assertEquals("line1\n\nline3", trimIndent("    line1\n\n    line3"));
        assertEquals("\nline1\nline2\nline3", trimIndent("\n    line1\n    line2\n    line3\n"));
        assertEquals("", trimIndent("\n\n\n"));
        assertEquals("line1\nline2", trimIndent("  line1\r\n  line2"));
        assertEquals("line1\nline2", trimIndent("\tline1\n\tline2"));
        assertEquals("line1\nline2", trimIndent("  line1\r\n  line2"));
        assertEquals("line1\n\nline2", trimIndent("line1\r\n\r\nline2"));
    }

    @Test
    void testTrimLines() {
        assertEquals("Line1\nLine2\nLine3\n", trimLines(new String[]{"Line1", "Line2", "Line3"}, 0).toString());
        assertEquals("Line1\n    Line2\nLine3\n", trimLines(new String[]{"    Line1", "        Line2", "    Line3"}, 4).toString());
        assertEquals("  Line1\nLine2\nLine3\n", trimLines(new String[]{"    Line1", "\tLine2", "  Line3"}, 2).toString());
        assertEquals("\n\n\n", trimLines(new String[]{"    ", "\t", "  "}, 2).toString());
        assertEquals("\n\n\n", trimLines(new String[]{"", "", ""}, 0).toString());
        assertEquals("Line1\nLine2\nLine3\n", trimLines(new String[]{"    Line1", "  Line2", "    Line3"}, 6).toString());
        assertEquals("Line1\nLine2\n", trimLines(new String[]{"  Line1\r", "\tLine2\r"}, 2).toString());
        assertEquals("Line1\nLine2\n", trimLines(new String[]{"\tLine1", "\tLine2"}, 1).toString());
        assertEquals("Line1\nLine2\nLine3\n", trimLines(new String[]{"  Line1\r", "  Line2", "  Line3"}, 2).toString());
        assertEquals("Line1\n\nLine3\n", trimLines(new String[]{"  Line1", "", "  Line3"}, 2).toString());
    }

    @Test
    void testGetCurrentIndent() {
        assertEquals(0, getCurrentIndent("NoIndentation"));
        assertEquals(4, getCurrentIndent("    FourSpaces"));
        assertEquals(3, getCurrentIndent("\t\t\tThreeTabs"));
        assertEquals(5, getCurrentIndent("  \t \tMixedSpacesAndTabs"));
        assertEquals(0, getCurrentIndent(""));
        assertEquals(6, getCurrentIndent("      "));
        assertEquals(3, getCurrentIndent("  \rLineWithCarriageReturn"));
        assertEquals(1, getCurrentIndent("\tLineWithTab"));
        assertEquals(1, getCurrentIndent("\rLineStartsWithCarriageReturn"));
    }

    @Test
    void testGetMinIndent() {
        assertEquals(0, getMinIndent(new String[]{"Line1", "Line2", "Line3"}));
        assertEquals(2, getMinIndent(new String[]{"    Line1", "  Line2", "        Line3"}));
        assertEquals(1, getMinIndent(new String[]{"    Line1", "\tLine2", "  Line3"}));
        assertEquals(Integer.MAX_VALUE, getMinIndent(new String[]{"    ", "\t", ""}));
        assertEquals(0, getMinIndent(new String[]{"    Line1", "", "  Line2", "Line3"}));
        assertEquals(Integer.MAX_VALUE, getMinIndent(new String[]{"", " ", "\t"}));
        assertEquals(2, getMinIndent(new String[]{"  Line1\r", "  Line2"}));
        assertEquals(1, getMinIndent(new String[]{"\tLine1", " Line2"}));
        assertEquals(0, getMinIndent(new String[]{"Line1\r", "\tLine2", "Line3"}));
    }
}