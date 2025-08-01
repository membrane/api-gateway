/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.util.StringUtil.truncateAfter;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    private static final String POEM = "To the greene forest so pleasant and faire";

    @Test
    void truncateAfterTest() {
        assertEquals("", truncateAfter(POEM, 0));
        assertEquals("To the greene", truncateAfter(POEM, 13));
        assertEquals(POEM, truncateAfter(POEM, POEM.length()));
        assertEquals(POEM, truncateAfter(POEM, 1000));
    }

    @Test
    public void testTruncateAfter_onlyPrintable() {
        String input = "GET /index.html HTTP/1.1";
        assertEquals(input, StringUtil.truncateAfter(input, 50));
    }

    @Test
    public void testTruncateAfter_truncateOnly() {
        String input = "This is a long line that should be cut off early.";
        assertEquals("This is a long line that should be cut off ea", StringUtil.truncateAfter(input, 45));
    }

    @Test
    public void testTruncateAfter_shorterThanMax() {
        String input = "Short line";
        assertEquals("Short line", StringUtil.truncateAfter(input, 100));
    }

    @Test
    public void testTruncateAfter_emptyString() {
        assertEquals("", StringUtil.truncateAfter("", 10));
    }

    @Test
    public void testTruncateAfter_zeroLength() {
        assertEquals("", StringUtil.truncateAfter("test", 0));
    }

    @Test
    void testMaskNonPrintableCharacters_withOnlyPrintable() {
        String input = "Hello, World! 123";
        assertEquals(input, StringUtil.maskNonPrintableCharacters(input));
    }

    @Test
    void testMaskNonPrintableCharacters_withNonPrintable() {
        assertEquals("_?U__Z___huv_D", StringUtil.maskNonPrintableCharacters("\u00e6?\u0055\u00d6\u00ff\u005a\u00a9\u00ae\u00a7huv\u00a8D"));
    }

    @Test
    void testMaskNonPrintableCharacters_onlyNonPrintable() {
        assertEquals("____", StringUtil.maskNonPrintableCharacters("\n\r\t\b"));
    }

    @Test
    void testMaskNonPrintableCharacters_mixedCharacters() {
        assertEquals("A_B_C", StringUtil.maskNonPrintableCharacters("A\tB\nC"));
    }

    @Test
    void testMaskNonPrintableCharacters_emptyString() {
        assertEquals("", StringUtil.maskNonPrintableCharacters(""));
    }

}