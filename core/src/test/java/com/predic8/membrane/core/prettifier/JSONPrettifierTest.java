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

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class JSONPrettifierTest {

    private static final byte[] s = """
                { "foo": { "bar":  { "baz": 7 }}}
                """.getBytes(UTF_8);

    JSONPrettifier beautifier;

    @BeforeEach
    void setUp() {
        beautifier = new JSONPrettifier();
    }

    @Test
    void beautifySimple() {
        String beautified = new String(beautifier.prettify(s, UTF_8), UTF_8);

        assertTrue(beautified.contains("\"foo\""));
        assertTrue(beautified.contains("\"bar\""));
        assertTrue(beautified.contains("\"baz\""));

        assertTrue(beautified.contains(" "));
        assertTrue(beautified.contains("\n"));
    }

    @Test
    void alreadyPrettyPrintedJson_isIdempotent() {
        byte[] pretty1 = beautifier.prettify(s);
        byte[] pretty2 = beautifier.prettify(pretty1);

        assertArrayEquals(pretty1, pretty2);
    }

    @Nested
    class JSON5Features {

        @Nested
        class Comments {
            @Test
            void lineAndBlockCommentsAreIgnored() {
                byte[] withComments = """
                    {
                      // line comment
                      foo: 1, /* block
                                comment */
                      bar: /* inline */ 2
                    }
                    """.getBytes(UTF_8);

                String beautified = new String(beautifier.prettify(withComments), UTF_8);

                assertTrue(beautified.contains("\"foo\""));
                assertTrue(beautified.contains("\"bar\""));
                // Pretty output should not contain comment markers
                assertFalse(beautified.contains("//"));
                assertFalse(beautified.contains("/*"));
            }
        }

        @Nested
        class TrailingCommas {
            @Test
            void objectWithTrailingComma() {
                byte[] objWithTrailingComma = """
                    { "a": 1, "b": 2, }
                    """.getBytes(UTF_8);

                String beautified = new String(beautifier.prettify(objWithTrailingComma), UTF_8);

                assertTrue(beautified.contains("\"a\""));
                assertTrue(beautified.contains("\"b\""));
            }

            @Test
            void arrayWithTrailingComma() {
                byte[] arrWithTrailingComma = """
                    { "nums": [1,2,3,] }
                    """.getBytes(UTF_8);

                String beautified = new String(beautifier.prettify(arrWithTrailingComma), UTF_8);

                assertTrue(beautified.contains("\"nums\""));
                assertTrue(beautified.contains("[ 1, 2, 3 ]") || beautified.contains("[1, 2, 3]"));
            }
        }

        @Nested
        class SingleQuotes {
            @Test
            void singleQuotedStrings() {
                byte[] singleQuoted = """
                    { 'name': 'Alice', 'city': 'Berlin' }
                    """.getBytes(UTF_8);

                String beautified = new String(beautifier.prettify(singleQuoted), UTF_8);

                // Jackson normalizes to double quotes
                assertTrue(beautified.contains("\"name\""));
                assertTrue(beautified.contains("\"Alice\""));
                assertTrue(beautified.contains("\"city\""));
                assertTrue(beautified.contains("\"Berlin\""));
            }
        }

        @Nested
        class UnquotedFieldNames {
            @Test
            void simpleIdentifiersWithoutQuotes() {
                byte[] unquoted = """
                    { foo: 1, bar_baz: 2 }
                    """.getBytes(UTF_8);

                String beautified = new String(beautifier.prettify(unquoted), UTF_8);

                // Keys should be quoted in the output
                assertTrue(beautified.contains("\"foo\""));
                assertTrue(beautified.contains("\"bar_baz\""));
            }
        }

        @Nested
        class Combination {
            @Test
            void allFeaturesTogether() {
                byte[] combo = """
                    {
                      // comment
                      foo: 'x',
                      'bar':  [1, 2, 3,], /* block comment */
                      baz: { a: 1, b: 2, },
                    }
                    """.getBytes(UTF_8);

                byte[] pretty = beautifier.prettify(combo);
                String beautified = new String(pretty, UTF_8);

                // Structure preserved, normalized JSON produced
                assertTrue(beautified.contains("\"foo\""));
                assertTrue(beautified.contains("\"bar\""));
                assertTrue(beautified.contains("\"baz\""));
                assertTrue(beautified.contains("\"a\""));
                assertTrue(beautified.contains("\"b\""));

                // No comments in output
                assertFalse(beautified.contains("//"));
                assertFalse(beautified.contains("/*"));

                // Idempotent even with JSON5 inputs
                assertArrayEquals(pretty, beautifier.prettify(pretty));
            }
        }
    }
}