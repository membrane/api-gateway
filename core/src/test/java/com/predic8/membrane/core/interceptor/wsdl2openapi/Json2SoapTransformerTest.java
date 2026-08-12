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
import java.util.HashMap;
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
    static Definitions refChildDefinitions;
    static Definitions inheritedDefinitions;
    static Definitions extendedTypesDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        emptyMessageDefinitions = Definitions.parse(new ResolverMap(), "classpath:/special/empty-message.wsdl");
        orderingDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/ordering.wsdl");
        attributesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/attributes.wsdl");
        qualifiedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/qualified-elements.wsdl");
        crossNamespaceChoiceDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace-choice.wsdl");
        refChildDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/ref-child.wsdl");
        inheritedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/inherited-content-model.wsdl");
        extendedTypesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/extended-types.wsdl");
    }

    private static final String REF_DETAIL_NS = "https://example.com/ref-detail";

    /** placeOrder declares ref'd "shipment" before local "orderId"; the JSON deliberately reverses both levels. */
    private static final String PLACE_ORDER_JSON =
            "{\"orderId\": \"A-1\", \"shipment\": {\"city\": \"Berlin\", \"street\": \"Main St\"}}";

    @Test
    void refChildIsEmittedInSchemaDeclarationOrder() throws Exception {
        var soapBytes = new Json2SoapTransformer(refChildDefinitions, "placeOrder").transform(PLACE_ORDER_JSON);

        Element placeOrderEl = getFirstChildElement(bodyOf(parseXml(soapBytes)));
        var children = getChildElements(placeOrderEl).stream().map(Element::getLocalName).toList();

        assertEquals(List.of("shipment", "orderId"), children,
                "a ref'd child must take its declared position, not be appended after the named ones");
    }

    /**
     * Covers both halves of the child-context resolution: the ref'd element has to be found at all,
     * and it has to be resolved against its <em>own</em> schema document — "shipment" carries an
     * unprefixed {@code type="ShipmentType"}, which only resolves against the schema that declares
     * it, so a context built on the referrer's root finds no complexType and orders nothing.
     */
    @Test
    void refChildSubtreeKeepsSchemaOrder() throws Exception {
        var soapBytes = new Json2SoapTransformer(refChildDefinitions, "placeOrder").transform(PLACE_ORDER_JSON);

        Element placeOrderEl = getFirstChildElement(bodyOf(parseXml(soapBytes)));
        Element shipmentEl = getFirstChildElement(placeOrderEl);

        assertEquals(REF_DETAIL_NS, shipmentEl.getNamespaceURI(), "the ref'd element takes its own target namespace");
        assertEquals(List.of("street", "city"),
                getChildElements(shipmentEl).stream().map(Element::getLocalName).toList(),
                "the referenced element's own sequence must order its children");
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

    // --- inherited and grouped content models ---

    private static final String BASE_NS = "https://example.com/inherit-base";
    private static final String SERVICE_NS = "https://example.com/inherit-service";

    /** Deliberately reversed at both levels, so only the XSD can produce the right order. */
    private static final String PRIORITY_ORDER_JSON = """
            {"createdBy": "bob", "priority": "high", \
            "address": {"city": "Berlin", "street": "Main St"}, "orderId": "A-1"}""";

    @Test
    void inheritedFieldsPrecedeDerivedOnesInSchemaOrder() throws Exception {
        var soapBytes = new Json2SoapTransformer(inheritedDefinitions, "placeOrder")
                .transform(PRIORITY_ORDER_JSON);

        Element placeOrderEl = getFirstChildElement(bodyOf(parseXml(soapBytes)));
        var children = getChildElements(placeOrderEl).stream().map(Element::getLocalName).toList();

        assertEquals(List.of("orderId", "address", "priority", "createdBy"), children,
                "the base type's content model comes first, then the extension's, with the group expanded in place");
    }

    @Test
    void inheritedFieldsCarryTheNamespaceOfTheSchemaDeclaringThem() throws Exception {
        var soapBytes = new Json2SoapTransformer(inheritedDefinitions, "placeOrder")
                .transform(PRIORITY_ORDER_JSON);

        var byName = new HashMap<String, String>();
        for (Element el : getChildElements(getFirstChildElement(bodyOf(parseXml(soapBytes))))) {
            byName.put(el.getLocalName(), el.getNamespaceURI());
        }

        assertEquals(BASE_NS, byName.get("orderId"), "a field declared in the base type's schema takes its namespace");
        assertEquals(BASE_NS, byName.get("createdBy"), "so does a field pulled in from a group in that schema");
        assertEquals(SERVICE_NS, byName.get("priority"), "the derived type's own field keeps the deriving schema's namespace");
    }

    @Test
    void descendingIntoAnInheritedComplexChildUsesItsOwnSchema() throws Exception {
        var soapBytes = new Json2SoapTransformer(inheritedDefinitions, "placeOrder")
                .transform(PRIORITY_ORDER_JSON);

        Element addressEl = getChildElements(getFirstChildElement(bodyOf(parseXml(soapBytes)))).get(1);
        var addressChildren = getChildElements(addressEl).stream().map(Element::getLocalName).toList();

        assertEquals("address", addressEl.getLocalName());
        assertEquals(List.of("street", "city"), addressChildren,
                "a complex child of the base type must still order its own fields from the XSD");
        assertEquals(BASE_NS, addressEl.getNamespaceURI());
    }

    @Test
    void fieldsInsideANestedChoiceAreOrderedFromTheXsd() throws Exception {
        var soapBytes = new Json2SoapTransformer(extendedTypesDefinitions, "search")
                .transform("{\"code\": \"A\", \"byName\": \"Berlin\"}");

        Element searchEl = getFirstChildElement(bodyOf(parseXml(soapBytes)));
        var children = getChildElements(searchEl).stream().map(Element::getLocalName).toList();

        assertEquals(List.of("byName", "code"), children,
                "an element inside a choice nested in the sequence must take its declared position");
    }

    // --- null values / xsi:nil ---

    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    @Test
    void nullValueBecomesNilElementRatherThanTheTextNull() throws Exception {
        var soapBytes = new Json2SoapTransformer(attributesDefinitions, "record")
                .transform("{\"name\": null}");

        Element nameEl = getFirstChildElement(getFirstChildElement(bodyOf(parseXml(soapBytes))));

        assertNotNull(nameEl, "a null value must still emit the element");
        assertEquals("name", nameEl.getLocalName());
        assertEquals("true", nameEl.getAttributeNS(XSI_NS, "nil"), "a null must be marked xsi:nil");
        assertEquals("", nameEl.getTextContent(), "a nil element must be empty, not carry the text \"null\"");
    }

    @Test
    void nullAttributeIsOmitted() throws Exception {
        var soapBytes = new Json2SoapTransformer(attributesDefinitions, "record")
                .transform("{\"name\": \"Berlin\", \"@id\": null, \"@type\": \"city\"}");

        Element recordEl = getFirstChildElement(bodyOf(parseXml(soapBytes)));

        assertFalse(recordEl.hasAttribute("id"), "an attribute cannot be nil, so a null omits it");
        assertEquals("city", recordEl.getAttribute("type"), "the other attribute is unaffected");
    }

    @Test
    void nullArrayItemBecomesNilElement() throws Exception {
        var soapBytes = new Json2SoapTransformer(attributesDefinitions, "record")
                .transform("{\"name\": [\"Berlin\", null, \"Bonn\"]}");

        var names = getChildElements(getFirstChildElement(bodyOf(parseXml(soapBytes))));

        assertEquals(3, names.size(), "a null occurrence must still emit an element");
        assertEquals("Berlin", names.get(0).getTextContent());
        assertEquals("true", names.get(1).getAttributeNS(XSI_NS, "nil"));
        assertEquals("Bonn", names.get(2).getTextContent());
    }

    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(bytes));
    }

    private static Element bodyOf(Document doc) {
        NodeList bodies = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body");
        return (Element) bodies.item(0);
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
