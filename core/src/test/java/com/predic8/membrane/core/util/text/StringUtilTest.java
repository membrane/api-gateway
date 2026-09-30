/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.text.StringUtil.getLastNonEmptyOfCommaSeparatedString;
import static com.predic8.membrane.core.util.text.StringUtil.tail;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilTest {

    @Test
    void testTail() {
        assertEquals("def", tail("abcdef",3));
        assertEquals("abcdef", tail("abcdef",10));
        assertEquals("", tail("",10));
    }

    @Nested
    class GetLastNonEmptyOfCommaSeparatedString {

        @Test
        void valueWithoutCommaIsTheLastElement() {
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("chunked"));
        }

        @Test
        void lastElementOfAList() {
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("gzip, chunked"));
            assertEquals("c", getLastNonEmptyOfCommaSeparatedString("a,b,c"));
        }

        /**
         * HTTP list elements may be surrounded by optional whitespace (RFC 9110 5.6.1), which is
         * not part of the element.
         */
        @Test
        void surroundingWhitespaceIsStripped() {
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("gzip,   chunked  "));
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("  chunked\t"));
        }

        @Test
        void separatorWithoutWhitespace() {
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("gzip,chunked"));
        }

        /**
         * Only the separators are split on, so anything else the element carries survives.
         */
        @Test
        void innerPunctuationOfTheLastElementIsKept() {
            assertEquals("b;q=0.5", getLastNonEmptyOfCommaSeparatedString("a, b;q=0.5"));
        }

        /**
         * Empty list elements are legal and are ignored (RFC 9110 5.6.1.2), so a trailing
         * separator does not hide the coding in front of it.
         */
        @Test
        void trailingSeparatorIsIgnored() {
            assertEquals("gzip", getLastNonEmptyOfCommaSeparatedString("gzip,"));
            assertEquals("gzip", getLastNonEmptyOfCommaSeparatedString("gzip, "));
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("gzip, chunked,,"));
        }

        @Test
        void interiorEmptyElementIsIgnored() {
            assertEquals("chunked", getLastNonEmptyOfCommaSeparatedString("gzip,, chunked"));
        }

        /**
         * Only empty elements are skipped: a non-empty last element stays the last one.
         */
        @Test
        void nonEmptyLastElementIsNotSkipped() {
            assertEquals("gzip", getLastNonEmptyOfCommaSeparatedString("chunked, gzip,"));
        }

        @Test
        void separatorsOnlyYieldAnEmptyString() {
            assertEquals("", getLastNonEmptyOfCommaSeparatedString(","));
            assertEquals("", getLastNonEmptyOfCommaSeparatedString(" , "));
        }

        @Test
        void emptyValue() {
            assertEquals("", getLastNonEmptyOfCommaSeparatedString(""));
            assertEquals("", getLastNonEmptyOfCommaSeparatedString("   "));
        }
    }
}