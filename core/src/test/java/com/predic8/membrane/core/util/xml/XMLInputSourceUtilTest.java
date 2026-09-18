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
package com.predic8.membrane.core.util.xml;

import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XMLInputSourceUtilTest {

    private static final String GREETING_XML = "<greeting>Österreich</greeting>";

    /**
     * Declares ISO-8859-1 but is actually ISO-8859-15: byte 0xA4 is the currency sign (¤) in the
     * former and the euro sign (€) in the latter, one of the few code points the two disagree on.
     */
    private static byte[] contradictingCharsetXml() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><price>".getBytes(ISO_8859_1));
        out.write(0xA4);
        out.writeBytes("</price>".getBytes(ISO_8859_1));
        return out.toByteArray();
    }

    /**
     * A byte order mark identifies the byte-stream encoding directly, so it must win over a
     * contradicting declared charset instead of being forced into a decoding that cannot parse
     * the bytes. Centralized regression coverage for the pattern behind issue #3279 - every
     * InputSource consumer (SOAP-to-JSON conversion, XPath, XmlDomBody, ...) gets this for free.
     */
    @Test
    void bomWinsOverAContradictingDeclaredCharset() {
        byte[] utf16WithBom = GREETING_XML.getBytes(UTF_16);

        InputSource source = XMLInputSourceUtil.getInputSource(new ByteArrayInputStream(utf16WithBom), ISO_8859_1.name());

        Document doc = HardenedXmlParser.getInstance().parse(source);
        assertEquals("Österreich", doc.getDocumentElement().getTextContent());
    }

    /**
     * Without a BOM, the declared charset still wins over the XML declaration - RFC 7303,
     * unchanged from before centralization.
     */
    @Test
    void declaredCharsetWinsOverTheXmlDeclarationWhenThereIsNoBom() {
        InputSource source = XMLInputSourceUtil.getInputSource(new ByteArrayInputStream(contradictingCharsetXml()), "ISO-8859-15");

        Document doc = HardenedXmlParser.getInstance().parse(source);
        assertEquals("€", doc.getDocumentElement().getTextContent());
    }

    /**
     * An unresolvable charset name must not blow up the caller; it falls back to leaving the
     * encoding unset so the parser detects it from the XML declaration instead.
     */
    @Test
    void unresolvableCharsetNameFallsBackToParserDetection() {
        InputSource source = XMLInputSourceUtil.getInputSource(new ByteArrayInputStream(GREETING_XML.getBytes(UTF_8)), "not-a-real-charset");

        assertNull(source.getEncoding());
    }

    /**
     * The BOM peek must fill its buffer across multiple reads, not assume one read(byte[])
     * call returns every byte requested - a stream delivering one byte at a time (as some
     * network streams do) must still be recognized as starting with a BOM.
     */
    @Test
    void bomIsDetectedWhenStreamReturnsOneByteAtATime() {
        byte[] utf16WithBom = GREETING_XML.getBytes(UTF_16);

        InputSource source = XMLInputSourceUtil.getInputSource(new OneByteAtATimeInputStream(utf16WithBom), ISO_8859_1.name());

        Document doc = HardenedXmlParser.getInstance().parse(source);
        assertEquals("Österreich", doc.getDocumentElement().getTextContent());
    }

    private static class OneByteAtATimeInputStream extends ByteArrayInputStream {
        OneByteAtATimeInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return super.read(b, off, len == 0 ? 0 : 1);
        }
    }

}
