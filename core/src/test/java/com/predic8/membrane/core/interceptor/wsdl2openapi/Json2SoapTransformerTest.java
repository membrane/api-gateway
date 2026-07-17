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

package com.predic8.membrane.core.interceptor.wsdl2openapi;

import com.predic8.membrane.core.resolver.ResolverMap;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class Json2SoapTransformerTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
    }

    @Test
    void producesSoap11EnvelopeForCitiesWsdl() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        Element envelope = doc.getDocumentElement();

        assertEquals("Envelope", envelope.getLocalName());
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", envelope.getNamespaceURI());
    }

    @Test
    void soapBodyContainsOperationElement() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        assertEquals(1, bodies.getLength(), "Should have exactly one SOAP Body");

        Element body = (Element) bodies.item(0);
        Element getCityElement = getFirstChildElement(body);
        assertNotNull(getCityElement, "Body should contain the operation element");
        assertEquals("getCity", getCityElement.getLocalName(), "Operation element should be named getCity");
    }

    @Test
    void operationElementHasCorrectNamespace() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element getCityElement = getFirstChildElement(body);

        assertEquals("https://predic8.de/cities", getCityElement.getNamespaceURI(),
                "Operation element should have the WSDL target namespace");
    }

    @Test
    void jsonFieldsMappedToChildElements() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element getCityElement = getFirstChildElement(body);

        NodeList nameElements = getCityElement.getElementsByTagName("name");
        assertEquals(1, nameElements.getLength(), "Should have a <name> child element");
        assertEquals("Berlin", nameElements.item(0).getTextContent(), "name element should contain 'Berlin'");
    }

    @Test
    void multipleJsonFieldsAllMapped() throws Exception {
        var transformer = new Json2SoapTransformer(blzDefinitions, "getBank");
        var soapBytes = transformer.transform("{\"blz\": \"12345678\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element getBankElement = getFirstChildElement(body);

        assertEquals("getBank", getBankElement.getLocalName());
        assertEquals("http://thomas-bayer.com/blz/", getBankElement.getNamespaceURI());

        NodeList blzElements = getBankElement.getElementsByTagName("blz");
        assertEquals(1, blzElements.getLength(), "Should have <blz> child element");
        assertEquals("12345678", blzElements.item(0).getTextContent());
    }

    @Test
    void jsonArrayMapsToRepeatedElements() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": [\"Berlin\", \"Paris\"]}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element getCityElement = getFirstChildElement(body);

        NodeList nameElements = getCityElement.getElementsByTagName("name");
        assertEquals(2, nameElements.getLength(), "Array should produce two <name> elements");
        assertEquals("Berlin", nameElements.item(0).getTextContent());
        assertEquals("Paris", nameElements.item(1).getTextContent());
    }

    @Test
    void unknownOperationThrowsException() {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "nonExistentOperation");

        assertThrows(Exception.class, () -> transformer.transform("{\"foo\": \"bar\"}"),
                "Should throw for unknown operation");
    }

    @Test
    void emptyJsonObjectProducesOperationElementWithNoChildren() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element getCityElement = getFirstChildElement(body);

        assertNotNull(getCityElement, "Should produce operation element even for empty JSON");
        assertEquals("getCity", getCityElement.getLocalName());
    }

    @Test
    void outputIsWellFormedXml() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        assertDoesNotThrow(() -> parseXml(soapBytes), "Output should be well-formed XML");
    }

    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private static Element getFirstChildElement(Element parent) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                return (Element) children.item(i);
            }
        }
        return null;
    }
}
