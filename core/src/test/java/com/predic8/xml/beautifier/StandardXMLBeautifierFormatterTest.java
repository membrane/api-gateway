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

package com.predic8.xml.beautifier;

import org.junit.jupiter.api.*;

import java.io.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class StandardXMLBeautifierFormatterTest {

    private StandardXMLBeautifierFormatter newFormatter(StringWriter w) {
        // indent used only by indent(); most methods ignore it
        return new StandardXMLBeautifierFormatter(w, 2);
    }

    @Test
    void startTag_writeTag_closeTag_withoutPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.startTag();
        f.writeTag(null, "root");
        f.closeTag(); // '>' of the start tag

        assertEquals("<root>", w.toString());
    }

    @Test
    void startTag_writeTag_closeTag_withPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.startTag();
        f.writeTag("ns", "root");
        f.closeTag();

        assertEquals("<ns:root>", w.toString());
    }

    @Test
    void closeTag_withName() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.closeTag("ns", "root");
        assertEquals("</ns:root>", w.toString());
    }

    @Test
    void closeEmptyTag_writesSpaceSlashGt() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.closeEmptyTag();
        assertEquals(" />", w.toString());
    }

    @Test
    void writeAttribute_withoutPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeAttribute(null, "class", "c");
        assertEquals("class=\"c\"", w.toString());
    }

    @Test
    void writeAttribute_withPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeAttribute("ns", "id", "42");
        assertEquals("ns:id=\"42\"", w.toString());
    }

    @Test
    void writeAttribute_ignoresNullNameOrValue() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeAttribute(null, null, "x");
        f.writeAttribute(null, "a", null);
        assertEquals("", w.toString());
    }

    @Test
    void writeNamespaceAttribute_withoutPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeNamespaceAttribute(null, "urn:x");
        assertEquals(" xmlns=\"urn:x\"", w.toString());
    }

    @Test
    void writeNamespaceAttribute_withPrefix() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeNamespaceAttribute("ns", "urn:x");
        assertEquals(" xmlns:ns=\"urn:x\"", w.toString());
    }

    @Test
    void writeNamespaceAttribute_ignoresNullUri() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeNamespaceAttribute("ns", null);
        assertEquals("", w.toString());
    }

    @Test
    void writeVersionAndEncoding_withEncoding_addsNewline() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeVersionAndEncoding("1.0", "UTF-8");
        assertTrue(w.toString().startsWith("""
            <?xml version="1.0" encoding="UTF-8"?>"""),"Starts with: '%s'".formatted(w.toString().substring(0,10)));
    }

    @Test
    void writeVersionAndEncoding_withoutEncoding_addsNewline() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeVersionAndEncoding("1.0", null);
        assertTrue( w.toString().startsWith("""
                <?xml version="1.0"?>"""),"Start with: '" + w.toString().substring(0,10) + "'\n");
    }

    @Test
    void writeVersionAndEncoding_nullVersion_writesNothing() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeVersionAndEncoding(null, UTF_8.name());
        assertEquals("", w.toString());
    }

    @Test
    void writeComment_andPrintNewLine() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeComment(" hello ");
        f.printNewLine();
        assertEquals("<!-- hello -->\n", w.toString());
    }

    @Test
    void writeText_andIndent() throws IOException {
        StringWriter w = new StringWriter();
        // set indent to 3 to verify indent() output is 3 spaces
        StandardXMLBeautifierFormatter f = new StandardXMLBeautifierFormatter(w, 3);

        f.indent();
        f.writeText("X");
        assertEquals("   X", w.toString());
    }

    @Test
    void writeTag_nullLocalName_isIgnored() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.writeTag("ns", null);
        assertEquals("", w.toString());
    }

    @Test
    void printNewLine_writesLF() throws IOException {
        StringWriter w = new StringWriter();
        StandardXMLBeautifierFormatter f = newFormatter(w);

        f.printNewLine();
        assertEquals("\n", w.toString());
    }
}
