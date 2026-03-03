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

import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.util.text.ToXMLSerializer.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ToXMLSerializerTest {

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", toXML(null));
    }

    @Test
    void stringIsEscapedXml11() {
        assertEquals("a &lt; b &amp; &quot;x&quot; &apos;y&apos;", toXML("a < b & \"x\" 'y'"));
    }

    @Test
    void nodeIsSerializedWithoutXmlDeclaration() throws Exception {
        var out = toXML(parseXml("<root><a>1</a></root>").getDocumentElement());
        assertFalse(out.startsWith("<?xml"), "XML declaration must be omitted");
        assertTrue(out.contains("<root>"));
        assertTrue(out.contains("<a>1</a>"));
        assertTrue(out.contains("</root>"));
    }

    @Test
    void nodeListIsSerializedByConcatenatingAllItems() throws Exception {
        var doc = parseXml("""
            <root>
                <item id="1"/>
                <item id="2"/>
            </root>""");

        // filter to element nodes only (because childNodes includes text nodes)
        var items = doc.getElementsByTagName("item");
        assertEquals(2, items.getLength());

        var out = toXML(items);

        // order matters
        assertTrue(out.contains("id=\"1\""), out);
        assertTrue(out.contains("id=\"2\""), out);
        assertTrue(out.indexOf("id=\"1\"") < out.indexOf("id=\"2\""), out);

        // should just be the two elements concatenated (no wrapper)
        assertTrue(out.startsWith("<item"));
        assertTrue(out.endsWith("/>") || out.endsWith("</item>"));
    }

    @Test
    void defaultFallsBackToStringValueWithoutEscaping() {
        assertEquals("42", toXML(42));
        assertEquals("true", toXML(true));
    }

    @Test
    void emptyNodeListProducesEmptyString() throws Exception {
        assertEquals("", toXML(parseXml("<root/>").getElementsByTagName("does-not-exist")));
    }

    private static Document parseXml(String xml) throws Exception {
        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));
    }
}