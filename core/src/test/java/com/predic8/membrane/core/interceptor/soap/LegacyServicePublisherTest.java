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
package com.predic8.membrane.core.interceptor.soap;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class LegacyServicePublisherTest {

    private DocumentBuilderFactory dbf;
    private LegacyServicePublisher publisher;

    @BeforeEach
    void setUp() {
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        publisher = new LegacyServicePublisher();
    }

    @Test
    void extractBodyFromSoapValidInputReturnsCleanXml() throws Exception {
        String soapMessage = """
        <?xml version="1.0" encoding="UTF-8"?>
        <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
            <s11:Body>
                <getCityResponse>
                    <country>Germany</country>
                    <population>327000</population>
                </getCityResponse>
            </s11:Body>
        </s11:Envelope>
        """;

        String result = publisher.extractBodyFromSoap(soapMessage);
        Document resultDoc = dbf.newDocumentBuilder()
                .parse(new InputSource(new StringReader(result)));
        assertEquals("getCityResponse", resultDoc.getDocumentElement().getNodeName());
        assertEquals("Germany", resultDoc.getElementsByTagName("country").item(0).getTextContent());
        assertEquals("327000", resultDoc.getElementsByTagName("population").item(0).getTextContent());
    }

    @Test
    void extractBodyFromSoapInvalidInputThrowsException() {
        String invalidSoap = "<invalid>Not a SOAP message</invalid>";

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> publisher.extractBodyFromSoap(invalidSoap));
        assertEquals("java.lang.RuntimeException: No SOAP Body element found", exception.getMessage());
    }

    @Test
    void domToStringValidDocumentReturnsFormattedXml() throws Exception {
        Document doc = dbf.newDocumentBuilder()
                .parse(new InputSource(new StringReader("<root><child>value</child></root>")));

        String result = publisher.domToString(doc);

        assertTrue(result.contains("<?xml"));
        assertTrue(result.contains("<root>"));
        assertTrue(result.contains("<child>value</child>"));
    }

    @Test
    void appendChildrenValidInputAppendsAllElements() throws Exception {
        Document sourceDoc = dbf.newDocumentBuilder()
                .parse(new InputSource(new StringReader("<root><a>1</a><b>2</b></root>")));
        Document targetDoc = dbf.newDocumentBuilder().newDocument();
        Element targetRoot = targetDoc.createElement("newRoot");
        targetDoc.appendChild(targetRoot);

        LegacyServicePublisher.appendChildren(sourceDoc.getDocumentElement(), targetDoc, targetRoot);

        assertEquals(2, targetRoot.getChildNodes().getLength());
        assertEquals("1", targetRoot.getElementsByTagName("a").item(0).getTextContent());
        assertEquals("2", targetRoot.getElementsByTagName("b").item(0).getTextContent());
    }

    @Test
    void domFromStringValidSoapReturnsFirstElement() throws Exception {
        String soap = """
            <s11:Envelope xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
                <s11:Body>
                    <response>
                        <data>test</data>
                    </response>
                </s11:Body>
            </s11:Envelope>
            """;

        Node result = publisher.domFromString(soap);

        assertEquals("response", result.getLocalName());
        assertTrue(result.hasChildNodes());
    }

    @Test
    void getFirstRealElementWithMixedContentReturnsFirstElement() throws Exception {
        Document doc = dbf.newDocumentBuilder().newDocument();
        Element root = doc.createElement("root");
        root.appendChild(doc.createTextNode("Some Foobar"));
        Element element = doc.createElement("realElement");
        root.appendChild(element);

        Node result = LegacyServicePublisher.getFirstRealElement(root.getFirstChild());

        assertEquals("realElement", result.getNodeName());
    }

    @Test
    void convertXmlToJsonSingleRootElementStripsWrapper() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <name>test</name>
                <value>123</value>
            </root>
            """;

        String result = publisher.convertXmlToJson(xml);
        JSONObject json = new JSONObject(result);
        assertEquals("test", json.getString("name"));
        assertEquals(123, json.getInt("value"));
    }
}