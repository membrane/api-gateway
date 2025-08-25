/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.prettifier;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.prettifier.TextPrettifier.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class TextPrettifierTest extends AbstractPrettifierTest {

    @BeforeEach
    void setUp() {
        prettifier = TextPrettifier.INSTANCE;
    }

    @Test
    void singleLineNoNewline() {
        assertEquals("hello", normalizeMultiline("hello"));
    }

    @Test
    void singleLineWithNewline() {
        assertEquals("hello", normalizeMultiline("hello\n"));
    }

    @Test
    void leadingAndTrailingEmptyLines() {
        assertEquals("hi there", normalizeMultiline("\n\n   hi there   \n\n"));
    }

    @Test
    void indentation() {
        var input = """
                
                public class Test {
                    void run() {
                        System.out.println("hi");
                    }
                }
                
                """;

        String expected = """
        public class Test {
            void run() {
                System.out.println("hi");
            }
        }""";

        assertEquals(expected, normalizeMultiline(input));
    }

    @Test
    void internalBlankLinePreserved() {
        assertEquals("a\n\nb", normalizeMultiline("a\n\nb"));
    }

    @Test
    void simple() {
        assertPretty("a", "a");
        assertPretty("a", " a ");
        assertPretty("a", "\ta\t");
        assertPretty("a \tb", "\t a \tb\t");
    }

    @Test
    void lines() {
        assertPretty("a", "\na\n");
        assertPretty("a\nb\nc", "\na\nb\nc\n\n");
        assertPretty("a\nb\n\tc", "\t\na\nb\n\tc\n\n");
        assertPretty("","\t\n\t" );
        assertPretty("a\n\nb", "a\r\n\r\nb\r\n");
    }

    @Test
    void indent() {
        assertPretty("a\nb", "  a\n  b");
        assertPretty(" a\nb", "  a\n b");
        assertPretty(" a\nb\n c", "  a\n b\n  c");

    }

    @Test
    void trimTrailingWhitespace() {
        assertPretty("a\nb", "a \nb\t \t");
    }

    private void assertPretty(String exp, String actual) {
        assertEquals(exp,  new String(prettifier.prettify(actual.getBytes(UTF_8)), UTF_8));
    }

    @Nested
    class Encoding {

        @Test
        void iso88591() throws Exception {
            assertEquals(REF_CONTENT, makePretty("/charsets/iso-8859-1.txt", ISO_8859_1));
        }

        @Test
        void utf8() throws Exception {
            assertEquals(REF_CONTENT, makePretty("/charsets/utf-8.txt", UTF_8));
        }

        @Test
        void utf16() throws Exception {
            assertEquals(REF_CONTENT, makePretty("/charsets/utf-16.txt", UTF_16));
        }
    }
}