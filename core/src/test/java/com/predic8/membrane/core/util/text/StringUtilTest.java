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

import static com.predic8.membrane.core.util.text.StringUtil.getLastOfCommaSeparatedString;
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
    class GetLastOfCommaSeparatedString {

        @Test
        void valueWithoutCommaIsTheLastElement() {
            assertEquals("chunked", getLastOfCommaSeparatedString("chunked"));
        }

        @Test
        void lastElementOfAList() {
            assertEquals("chunked", getLastOfCommaSeparatedString("gzip, chunked"));
            assertEquals("c", getLastOfCommaSeparatedString("a,b,c"));
        }

        /**
         * HTTP list elements may be surrounded by optional whitespace (RFC 9110 5.6.1), which is
         * not part of the element.
         */
        @Test
        void surroundingWhitespaceIsStripped() {
            assertEquals("chunked", getLastOfCommaSeparatedString("gzip,   chunked  "));
            assertEquals("chunked", getLastOfCommaSeparatedString("  chunked\t"));
        }

        @Test
        void separatorWithoutWhitespace() {
            assertEquals("chunked", getLastOfCommaSeparatedString("gzip,chunked"));
        }

        /**
         * Only the separators are split on, so anything else the element carries survives.
         */
        @Test
        void innerPunctuationOfTheLastElementIsKept() {
            assertEquals("b;q=0.5", getLastOfCommaSeparatedString("a, b;q=0.5"));
        }

        @Test
        void trailingSeparatorYieldsAnEmptyLastElement() {
            assertEquals("", getLastOfCommaSeparatedString("gzip,"));
            assertEquals("", getLastOfCommaSeparatedString("gzip, "));
        }

        @Test
        void separatorOnlyYieldsAnEmptyLastElement() {
            assertEquals("", getLastOfCommaSeparatedString(","));
        }

        @Test
        void emptyValue() {
            assertEquals("", getLastOfCommaSeparatedString(""));
            assertEquals("", getLastOfCommaSeparatedString("   "));
        }
    }
}