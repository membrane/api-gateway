/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.predic8.membrane.core.util.TextUtil.*;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.*;

public class TextUtilTest {

    @Test
    void camelToKebabSimple() {
        assertEquals("tasty-kebab", camelToKebab("tastyKebab"));
    }

    @Test
    void camelToKebabMulti() {
        assertEquals("a-lot-of-tasty-kebab", camelToKebab("aLotOfTastyKebab"));
    }

    @Test
    void camelToKebabNoChange() {
        assertEquals("kebab", camelToKebab("Kebab"));
    }

    @Test
    void testGlobToExpStarPrefixHost() {
        Pattern pattern = Pattern.compile(globToRegExp("*.predic8.de"));
        assertTrue(pattern.matcher("hgsjagdjhsa.predic8.de").matches());
        assertTrue(pattern.matcher("jhkj.predic8.de").matches());
        assertFalse(pattern.matcher("jhkj.predic8.com").matches());
    }

    @Test
    void testGlobToExpStarSuffixHost() {
        Pattern pattern = Pattern.compile(globToRegExp("predic8.*"));
        assertTrue(pattern.matcher("predic8.de").matches());
        assertTrue(pattern.matcher("predic8.com").matches());
        assertFalse(pattern.matcher("jhkj.predic8.de").matches());
    }

    @Test
    void testGlobToExpStarInfixHost() {
        Pattern pattern = Pattern.compile(globToRegExp("www.*.de"));
        assertTrue(pattern.matcher("www.predic8.de").matches());
        assertTrue(pattern.matcher("www.oio.de").matches());
        assertFalse(pattern.matcher("www.predic8.com").matches());
        assertFalse(pattern.matcher("www.predic8.co.uk").matches());
        assertFalse(pattern.matcher("services.predic8.de").matches());
    }

    @Test
    void testGlobToExpStarPrefixIp() {
        Pattern pattern = Pattern.compile(globToRegExp("*.68.5.122"));
        assertTrue(pattern.matcher("192.68.5.122").matches());
        assertFalse(pattern.matcher("192.68.5.123").matches());
    }

    @Test
    void testGlobToExpStarSuffixIp() {
        Pattern pattern = Pattern.compile(globToRegExp("192.68.7.*"));
        assertTrue(pattern.matcher("192.68.7.12").matches());
        assertTrue(pattern.matcher("192.68.7.4").matches());
        assertFalse(pattern.matcher("192.68.6.12").matches());
    }

    @Test
    void testGlobToExpStarInfixIp() {
        Pattern pattern = Pattern.compile(globToRegExp("192.68.*.15"));
        assertTrue(pattern.matcher("192.68.5.15").matches());
        assertTrue(pattern.matcher("192.68.24.15").matches());
        assertFalse(pattern.matcher("192.68.24.12").matches());
    }

    @Test
    void getLineFromMultilineStringTest() {
        assertEquals("ccc ccc", TextUtil.getLineFromMultilineString("""
                aaa aaa
                bb bb
                ccc ccc
                ddd dd ddd
                """, 3));
    }

    @Test
    void getLineFromMultilineStringOneLine() {
        assertEquals("aaa aaa", TextUtil.getLineFromMultilineString("""
                aaa aaa
                """, 1));
    }

    @Test
    void escapeQuoteSimple() {
        assertEquals("Test text with \\\" quotes", escapeQuotes("Test text with \" quotes"));
    }


    @Test
    void testUnifyIndent() {
        assertEquals("""
                line1
                line2
                line3""", unifyIndent("""
                line1
                line2
                line3"""));

        assertEquals("""
                line1
                line2
                line3""", unifyIndent("""
                    line1
                    line2
                    line3"""));

        assertEquals("""
                line1
                    line2
                    line3""", unifyIndent("""
                line1
                    line2
                    line3"""));

        assertEquals("""
                line1
                
                line3""", unifyIndent("""
                line1
                
                line3"""));

        assertEquals("""
                
                line1
                    line2
                line3""", unifyIndent("""
                
                    line1
                        line2
                    line3
                """));

        assertEquals("", unifyIndent("""
                
                
                """));

        assertEquals("""
                line1
                
                line2""", unifyIndent("""
                line1\r
                
                line2"""));

        assertEquals("""
                line1
                line2""", unifyIndent("""
                \tline1
                \tline2"""));

        assertEquals("""
                    line1
                    
                line2""", unifyIndent("""
                    line1\r
                
                line2"""));

        assertEquals("""
                line1
                
                line2""", unifyIndent("""
                    line1\r
                \r
                    line2"""));

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
        assertEquals(MAX_VALUE, getMinIndent(new String[]{"    ", "\t", ""}));
        assertEquals(0, getMinIndent(new String[]{"    Line1", "", "  Line2", "Line3"}));
        assertEquals(MAX_VALUE, getMinIndent(new String[]{"", " ", "\t"}));
        assertEquals(2, getMinIndent(new String[]{"  Line1\r", "  Line2"}));
        assertEquals(1, getMinIndent(new String[]{"\tLine1", " Line2"}));
        assertEquals(0, getMinIndent(new String[]{"Line1\r", "\tLine2", "Line3"}));
    }
}