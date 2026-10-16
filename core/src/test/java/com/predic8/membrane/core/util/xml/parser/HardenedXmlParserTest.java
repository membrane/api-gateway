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
package com.predic8.membrane.core.util.xml.parser;

import com.predic8.membrane.core.http.ReadingBodyException;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HardenedXmlParserTest {

    @Test
    void readingBodyExceptionIsNotTurnedIntoParseError() {
        final var failure = new ReadingBodyException("connection reset");
        final var source = new InputSource(new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        });
        assertSame(failure, assertThrows(ReadingBodyException.class, () -> HardenedXmlParser.getInstance().parse(source)));
    }

    @Test
    void doctypeIsParseError() {
        assertThrows(XmlParseException.class, () -> HardenedXmlParser.getInstance().parse(
                new InputSource(new StringReader("<!DOCTYPE a><a/>"))));
    }
}
