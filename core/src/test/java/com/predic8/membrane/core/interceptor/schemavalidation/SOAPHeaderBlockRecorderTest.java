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

package com.predic8.membrane.core.interceptor.schemavalidation;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class SOAPHeaderBlockRecorderTest {

    private static final QName FIRST = new QName("https://example.com/a", "First");
    private static final QName SECOND = new QName("https://example.com/b", "Second");

    private static final String SOAP11 = """
            <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
              <s11:Header>
                <a:First xmlns:a="https://example.com/a"><a:value>1</a:value></a:First>
                <b:Second xmlns:b="https://example.com/b" s11:mustUnderstand="1" s11:actor="http://schemas.xmlsoap.org/soap/actor/next" id="x">
                  <b:value>2</b:value>
                </b:Second>
              </s11:Header>
              <s11:Body><c:body xmlns:c="https://example.com/c">3</c:body></s11:Body>
            </s11:Envelope>
            """;

    @Test
    void recordsOnlyTheSelectedBlock() throws Exception {
        var blocks = record(SOAP11, FIRST);

        assertEquals(1, blocks.size());
        var out = serialize(blocks.getFirst());
        assertTrue(out.contains("First"), out);
        assertTrue(out.contains(">1<"), out);
        assertFalse(out.contains("Second"), out);
        assertFalse(out.contains("Envelope"), out);
        assertFalse(out.contains("Header"), out);
        assertFalse(out.contains("body"), out);
    }

    @Test
    void recordsBlocksInDocumentOrder() throws Exception {
        var blocks = record(SOAP11, SECOND, FIRST);

        assertEquals(2, blocks.size());
        assertTrue(serialize(blocks.getFirst()).contains("First"));
        assertTrue(serialize(blocks.getLast()).contains("Second"));
    }

    @Test
    void actionStopsRecording() throws Exception {
        var blocks = new ArrayList<RecordedElement>();

        assertFalse(SOAPHeaderBlockRecorder.forEach(new ByteArrayInputStream(SOAP11.getBytes(UTF_8)), Set.of(FIRST, SECOND), block -> {
            blocks.add(block);
            return false;
        }));
        assertEquals(1, blocks.size());
        assertTrue(serialize(blocks.getFirst()).contains("First"));
    }

    @Test
    void recordsLaterBlockWithoutSoapAttributes() throws Exception {
        var out = serialize(record(SOAP11, SECOND).getFirst());

        assertTrue(out.contains("Second"), out);
        assertTrue(out.contains(">2<"), out);
        assertTrue(out.contains("id=\"x\""), "attributes of the block itself are kept: " + out);
        assertFalse(out.contains("mustUnderstand"), out);
        assertFalse(out.contains("actor"), out);
        assertFalse(out.contains("First"), out);
    }

    /**
     * WS-Security marks a signed block with wsu:Id. Only the block's root is stripped of it.
     */
    @Test
    void recordsBlockWithoutWsuId() throws Exception {
        var out = serialize(record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                  <s11:Header>
                    <a:First xmlns:a="https://example.com/a" wsu:Id="id-1" id="x"><a:value wsu:Id="id-2">1</a:value></a:First>
                  </s11:Header>
                  <s11:Body/>
                </s11:Envelope>
                """, FIRST).getFirst());

        assertFalse(out.contains("id-1"), out);
        assertTrue(out.contains("id=\"x\""), out);
        assertTrue(out.contains("id-2"), out);
    }

    @Test
    void soap12() throws Exception {
        var out = serialize(record("""
                <s12:Envelope xmlns:s12="http://www.w3.org/2003/05/soap-envelope">
                  <s12:Header>
                    <a:First xmlns:a="https://example.com/a" s12:mustUnderstand="true" s12:role="urn:r" s12:relay="true">1</a:First>
                  </s12:Header>
                  <s12:Body/>
                </s12:Envelope>
                """, FIRST).getFirst());

        assertTrue(out.contains("First"), out);
        assertFalse(out.contains("mustUnderstand"), out);
        assertFalse(out.contains("role"), out);
        assertFalse(out.contains("relay"), out);
    }

    /**
     * A block's descendants may well be named {@code Header} themselves; only the direct child of
     * the envelope is the SOAP header.
     */
    @Test
    void nestedHeaderElementInBlock() throws Exception {
        var blocks = record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header>
                    <a:First xmlns:a="https://example.com/a"><s11:Header>inner</s11:Header></a:First>
                    <b:Second xmlns:b="https://example.com/b"/>
                  </s11:Header>
                  <s11:Body/>
                </s11:Envelope>
                """, SECOND);

        assertEquals(1, blocks.size());
        var out = serialize(blocks.getFirst());
        assertTrue(out.contains("Second"), out);
        assertFalse(out.contains("inner"), out);
    }

    /**
     * The body is not parsed, so an error in it does not matter here.
     */
    @Test
    void stopsAtTheEndOfTheHeader() throws Exception {
        var blocks = record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header><a:First xmlns:a="https://example.com/a"/></s11:Header>
                  <s11:Body><unclosed></s11:Body>
                </s11:Envelope>
                """, FIRST);

        assertEquals(1, blocks.size());
    }

    /**
     * A header after the body is rejected when the body is validated, see {@link SOAPXMLFilter}.
     */
    @Test
    void stopsAtTheBodyWithoutHeader() throws Exception {
        assertTrue(record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Body><unclosed></s11:Body>
                </s11:Envelope>
                """, FIRST).isEmpty());
    }

    @Test
    void malformedHeader() {
        assertThrows(SAXParseException.class, () -> record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                  <s11:Header><a:First xmlns:a="https://example.com/a"><unclosed></a:First></s11:Header>
                  <s11:Body/>
                </s11:Envelope>
                """, FIRST));
    }

    /**
     * A QName value like an xsi:type may use a prefix declared outside the block. Only the
     * prefixes the block uses are declared for it.
     */
    @Test
    void declaresOuterPrefixesTheBlockUses() throws Exception {
        var out = serialize(record("""
                <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xmlns:t="https://example.com/types" xmlns:v="https://example.com/values"
                              xmlns:unused="https://example.com/unused">
                  <s11:Header>
                    <a:First xmlns:a="https://example.com/a" xsi:type="t:FirstType"><a:ref> v:value </a:ref></a:First>
                  </s11:Header>
                  <s11:Body/>
                </s11:Envelope>
                """, FIRST).getFirst());

        assertTrue(out.contains("xmlns:t=\"https://example.com/types\""), out);
        assertTrue(out.contains("xmlns:v=\"https://example.com/values\""), out);
        assertFalse(out.contains("unused"), out);
    }

    /**
     * Errors found while validating the replay must point into the message.
     */
    @Test
    void replayKeepsTheLinesOfTheMessage() throws Exception {
        var source = (SAXSource) record(SOAP11, SECOND).getFirst().toSource();
        var lines = new ArrayList<String>();
        var reader = source.getXMLReader();
        reader.setContentHandler(new DefaultHandler() {
            private Locator locator;

            @Override
            public void setDocumentLocator(Locator locator) {
                this.locator = locator;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                lines.add(localName + ":" + locator.getLineNumber());
            }
        });
        reader.parse(source.getInputSource());

        assertEquals(List.of("Second:4", "value:5"), lines);
    }

    private static List<RecordedElement> record(String soap, QName... selected) throws Exception {
        var blocks = new ArrayList<RecordedElement>();
        assertTrue(SOAPHeaderBlockRecorder.forEach(new ByteArrayInputStream(soap.getBytes(UTF_8)), Set.of(selected), blocks::add));
        return blocks;
    }

    private static String serialize(RecordedElement block) throws Exception {
        var tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        var sw = new StringWriter();
        t.transform(block.toSource(), new StreamResult(sw));
        return sw.toString();
    }
}
