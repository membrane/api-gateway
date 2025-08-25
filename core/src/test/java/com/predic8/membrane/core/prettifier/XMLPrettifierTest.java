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

import static com.predic8.membrane.core.util.xml.XMLEncodingTestUtil.*;
import static com.predic8.membrane.test.TestUtil.*;
import static java.nio.charset.StandardCharsets.*;

class XMLPrettifierTest extends AbstractPrettifierTest {

    @BeforeEach
    void setUp() {
        prettifier = XMLPrettifier.INSTANCE;
    }

    // TODO test CDATA

    @Nested
    class Encoding {

        @Test
        void iso88591() throws Exception {
            assertChars(new String(prettifier.prettify(readResource("/charsets/iso-8859-1-unformatted.xml")), ISO_8859_1));
        }

        @Test
        void utf8() throws Exception {
            assertChars(new String(prettifier.prettify(readResource("/charsets/utf-8-unformatted.xml")), UTF_8));
        }

        @Test
        void utf16be() throws Exception {
            assertChars(new String(prettifier.prettify(readResource("/charsets/utf-16be-unformatted.xml"), UTF_16BE), UTF_16));
        }
    }
}