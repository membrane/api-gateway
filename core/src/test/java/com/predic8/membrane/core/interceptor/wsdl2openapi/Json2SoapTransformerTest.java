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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Json2SoapTransformerTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;
    static Definitions emptyMessageDefinitions;
    static Definitions orderingDefinitions;
    static Definitions attributesDefinitions;
    static Definitions qualifiedDefinitions;
    static Definitions crossNamespaceChoiceDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        emptyMessageDefinitions = Definitions.parse(new ResolverMap(), "classpath:/special/empty-message.wsdl");
        orderingDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/ordering.wsdl");
        attributesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/attributes.wsdl");
        qualifiedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/qualified-elements.wsdl");
        crossNamespaceChoiceDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace-choice.wsdl");
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
    void usesS11PrefixForSoap11Envelope() throws Exception {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "getCity");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        Element envelope = doc.getDocumentElement();

        assertEquals("s11", envelope.getPrefix());
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
    void unknownOperationThrowsIllegalArgumentException() {
        var transformer = new Json2SoapTransformer(citiesDefinitions, "nonExistentOperation");

        assertThrows(IllegalArgumentException.class, () -> transformer.transform("{\"foo\": \"bar\"}"),
                "Should throw IllegalArgumentException for unknown operation");
    }

    @Test
    void emptyMessagePartsThrowsIllegalArgumentException() {
        var transformer = new Json2SoapTransformer(emptyMessageDefinitions, "ping");

        assertThrows(IllegalArgumentException.class, () -> transformer.transform("{}"),
                "Should throw IllegalArgumentException when input message has no parts");
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

    @Test
    void fieldsOrderedAccordingToWsdlSchema() throws Exception {
        // JSON fields are in reverse WSDL order: age, lastName, firstName
        var transformer = new Json2SoapTransformer(orderingDefinitions, "createPerson");
        var soapBytes = transformer.transform("{\"age\": 30, \"lastName\": \"Doe\", \"firstName\": \"John\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element createPersonEl = getFirstChildElement(body);

        List<Element> children = getChildElements(createPersonEl);
        assertEquals(3, children.size());
        assertEquals("firstName", children.get(0).getLocalName());
        assertEquals("John", children.get(0).getTextContent());
        assertEquals("lastName", children.get(1).getLocalName());
        assertEquals("Doe", children.get(1).getTextContent());
        assertEquals("age", children.get(2).getLocalName());
        assertEquals("30", children.get(2).getTextContent());
    }

    // ── @-prefixed JSON keys mapped to XML attributes ─────────────────────

    @Test
    void jsonAtPrefixedFieldMappedToXmlAttribute() throws Exception {
        var transformer = new Json2SoapTransformer(attributesDefinitions, "record");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\", \"@id\": \"123\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element recordEl = getFirstChildElement(body);

        assertEquals("123", recordEl.getAttribute("id"), "@id should be mapped to an XML attribute");
        assertEquals(0, recordEl.getElementsByTagName("@id").getLength(),
                "@id should not appear as a child element");
        assertEquals(1, recordEl.getElementsByTagName("name").getLength(),
                "name should still be mapped to a normal child element");
    }

    @Test
    void multipleAtPrefixedFieldsMappedToMultipleXmlAttributes() throws Exception {
        var transformer = new Json2SoapTransformer(attributesDefinitions, "record");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\", \"@id\": \"123\", \"@type\": \"city\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element recordEl = getFirstChildElement(body);

        assertEquals("123", recordEl.getAttribute("id"));
        assertEquals("city", recordEl.getAttribute("type"));
        assertEquals(1, recordEl.getElementsByTagName("name").getLength(),
                "name should still be mapped to a normal child element");
    }

    @Test
    void missingAtPrefixedFieldProducesNoAttribute() throws Exception {
        var transformer = new Json2SoapTransformer(attributesDefinitions, "record");
        var soapBytes = transformer.transform("{\"name\": \"Berlin\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element recordEl = getFirstChildElement(body);

        assertEquals("", recordEl.getAttribute("id"), "id attribute should be absent when @id is not in the JSON");
    }

    @Test
    void choiceRefFromDifferentNamespaceGetsCorrectNamespace() throws Exception {
        var transformer = new Json2SoapTransformer(crossNamespaceChoiceDefinitions, "processInput");
        var soapBytes = transformer.transform("{\"numericInput\": 42}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element processInputEl = getFirstChildElement(body);

        Element numericInputEl = getFirstChildElement(processInputEl);
        assertNotNull(numericInputEl);
        assertEquals("numericInput", numericInputEl.getLocalName());
        assertEquals("https://example.com/choice-type-b", numericInputEl.getNamespaceURI(),
                "numericInput is ref'd from choice-type-b and must carry that namespace");
    }

    @Test
    void choiceRefSelectsCorrectAlternativeNamespace() throws Exception {
        var transformer = new Json2SoapTransformer(crossNamespaceChoiceDefinitions, "processInput");
        var soapBytes = transformer.transform("{\"textInput\": \"hello\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element processInputEl = getFirstChildElement(body);

        Element textInputEl = getFirstChildElement(processInputEl);
        assertNotNull(textInputEl);
        assertEquals("textInput", textInputEl.getLocalName());
        assertEquals("https://example.com/choice-type-a", textInputEl.getNamespaceURI(),
                "textInput is ref'd from choice-type-a and must carry that namespace");
    }

    @Test
    void choiceRefsWithSameLocalNameAreDisambiguatedByQualifiedJsonKey_alternativeA() throws Exception {
        var transformer = new Json2SoapTransformer(crossNamespaceChoiceDefinitions, "processAmbiguous");
        var soapBytes = transformer.transform("{\"{https://example.com/choice-type-a}value\": \"hello\"}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element processAmbiguousEl = getFirstChildElement(body);

        Element valueEl = getFirstChildElement(processAmbiguousEl);
        assertNotNull(valueEl);
        assertEquals("value", valueEl.getLocalName());
        assertEquals("https://example.com/choice-type-a", valueEl.getNamespaceURI());
        assertEquals("hello", valueEl.getTextContent());
    }

    @Test
    void choiceRefsWithSameLocalNameAreDisambiguatedByQualifiedJsonKey_alternativeB() throws Exception {
        var transformer = new Json2SoapTransformer(crossNamespaceChoiceDefinitions, "processAmbiguous");
        var soapBytes = transformer.transform("{\"{https://example.com/choice-type-b}value\": 42}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element processAmbiguousEl = getFirstChildElement(body);

        Element valueEl = getFirstChildElement(processAmbiguousEl);
        assertNotNull(valueEl);
        assertEquals("value", valueEl.getLocalName());
        assertEquals("https://example.com/choice-type-b", valueEl.getNamespaceURI());
        assertEquals("42", valueEl.getTextContent());
    }

    @Test
    void nestedObjectChildrenCarryTargetNamespaceWhenElementFormDefaultIsQualified() throws Exception {
        var transformer = new Json2SoapTransformer(qualifiedDefinitions, "createOrder");
        var soapBytes = transformer.transform(
                "{\"address\": {\"street\": \"Main St\", \"city\": \"Berlin\"}, \"amount\": 42}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element createOrderEl = getFirstChildElement(body);

        Element addressEl = getFirstChildElement(createOrderEl);
        assertNotNull(addressEl);
        assertEquals("address", addressEl.getLocalName());
        assertEquals("https://example.com/qualified", addressEl.getNamespaceURI());

        Element streetEl = getFirstChildElement(addressEl);
        assertNotNull(streetEl, "address should have a street child element");
        assertEquals("street", streetEl.getLocalName());
        assertEquals("https://example.com/qualified", streetEl.getNamespaceURI(),
                "Nested element must carry target namespace when elementFormDefault=qualified");
        assertEquals("Main St", streetEl.getTextContent());
    }

    @Test
    void childElementsInSoapCarryTargetNamespaceWhenElementFormDefaultIsQualified() throws Exception {
        var transformer = new Json2SoapTransformer(qualifiedDefinitions, "sendMessage");
        var soapBytes = transformer.transform("{\"text\": \"hello\", \"priority\": 1}");

        Document doc = parseXml(soapBytes);
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        Element body = (Element) bodies.item(0);
        Element sendMessageEl = getFirstChildElement(body);

        Element textEl = getFirstChildElement(sendMessageEl);
        assertNotNull(textEl, "Should have a child element");
        assertEquals("text", textEl.getLocalName());
        assertEquals("https://example.com/qualified", textEl.getNamespaceURI(),
                "Child element must carry target namespace when elementFormDefault=qualified");
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

    private static List<Element> getChildElements(Element parent) {
        var result = new ArrayList<Element>();
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                result.add((Element) children.item(i));
            }
        }
        return result;
    }
}
