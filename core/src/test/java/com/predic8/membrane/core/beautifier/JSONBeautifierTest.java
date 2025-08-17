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

package com.predic8.membrane.core.beautifier;

import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class JSONBeautifierTest {

    private static final byte[] s = """
                { "foo": { "bar":  { "baz": 7 }}}
                """.getBytes();

    JSONPrettifier beautifier;

    @BeforeEach
    void setUp() {
        beautifier = new JSONPrettifier();
    }

    @Test
    void beautifySimple() throws IOException {
        String beautified = new String(beautifier.prettify(s));

        assertTrue(beautified.contains("\"foo\""));
        assertTrue(beautified.contains("\"bar\""));
        assertTrue(beautified.contains("\"baz\""));

        assertTrue(beautified.contains(" "));
        assertTrue(beautified.contains("\n"));
    }

    @Test
    void alreadyPrettyPrintedJson_isIdempotent() throws IOException {
        byte[] pretty1 = beautifier.prettify(s);
        byte[] pretty2 = beautifier.prettify(pretty1);

        assertArrayEquals(pretty1, pretty2);
    }
}