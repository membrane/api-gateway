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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class XmlSchemaMappingTest {

    private static XmlSchemaMapping notes;
    private static XmlSchemaMapping library;
    private static XmlSchemaMapping review;
    private static XmlSchemaMapping catalog;

    @BeforeAll
    static void load() {
        notes = XmlSchemaMapping.load(new DummyTestRouter(), null, "classpath:/xml-schema/notes-a.xsd");
        library = XmlSchemaMapping.load(new DummyTestRouter(), null, "classpath:/xml-schema/library.xsd");
        review = XmlSchemaMapping.load(new DummyTestRouter(), null, "classpath:/xml-schema/review.xsd");
        catalog = XmlSchemaMapping.load(new DummyTestRouter(), null, "classpath:/xml-schema/catalog.xsd");
    }

    @Test
    void aLocalNameDeclaredInTwoNamespacesIsKeyedWithItsNamespace() throws Exception {
        final var json = new ObjectMapper().readTree(notes.toJson(new InputSource(new StringReader(
                "<note xmlns=\"https://example.com/b\"><text>hello</text></note>"))));

        assertEquals("hello", json.get("{https://example.com/b}note").get("text").asText(), json.toString());
    }

    @Test
    void aNamespaceQualifiedKeyWritesTheElementOfThatNamespace() throws Exception {
        final var xml = new String(notes.toXml("{\"{https://example.com/a}note\": {\"count\": 3}}"), UTF_8);

        final var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final var root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        assertEquals("https://example.com/a", root.getNamespaceURI(), xml);
        assertEquals("note", root.getLocalName(), xml);
        assertEquals("3", root.getElementsByTagNameNS("https://example.com/a", "count").item(0).getTextContent(), xml);
    }

    @Test
    void theUnqualifiedLocalNameOfAnAmbiguousElementIsUndeclared() {
        assertThrows(UndeclaredElementException.class, () -> notes.toXml("{\"note\": {}}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"price\": {\"$value\": 9.99}} {\"price\": {\"$value\": 12}}", "{\"price\": {\"$value\": 9.99}}]]] garbage"})
    void jsonWithInputAfterItsValueIsRejected(String json) {
        assertThrows(JsonProcessingException.class, () -> library.toXml(json));
    }

    @Test
    void aSimpleContentRootKeepsItsTypedValueNextToItsAttributes() throws Exception {
        final var json = new ObjectMapper().readTree(library.toJson(new InputSource(new StringReader(
                "<price currency=\"EUR\">9.99</price>"))));

        assertEquals("{\"price\":{\"@currency\":\"EUR\",\"$value\":9.99}}", json.toString());
    }

    @Test
    void aSimpleContentRootWithoutItsOptionalAttributeIsStillAnObject() throws Exception {
        final var json = new ObjectMapper().readTree(library.toJson(new InputSource(new StringReader(
                "<price>9.99</price>"))));

        assertEquals("{\"price\":{\"$value\":9.99}}", json.toString());
    }

    @Test
    void aSimpleContentChildWithoutItsOptionalAttributeHasTheShapeTheSchemaDeclares() throws Exception {
        final var json = new ObjectMapper().readTree(library.toJson(new InputSource(new StringReader("""
                <library>
                    <book id="1"><title>A</title><author>X</author><pages>10</pages>
                        <price currency="EUR">12.50</price><available>true</available><note>n</note></book>
                    <book id="2"><title>B</title><author>Y</author><pages>20</pages>
                        <price>9.99</price><available>false</available><note>n</note></book>
                </library>"""))));

        final var books = json.get("library").get("book");
        assertEquals("{\"@currency\":\"EUR\",\"$value\":12.5}", books.get(0).get("price").toString());
        assertEquals("{\"$value\":9.99}", books.get(1).get("price").toString());
    }

    @Test
    void anElementFromAnIncludeWithoutTargetNamespaceIsDeclaredInTheIncludingNamespace() throws Exception {
        final var json = new ObjectMapper().readTree(review.toJson(new InputSource(new StringReader("""
                <note xmlns="https://example.com/review"><text>hello</text><count>3</count></note>"""))));

        assertEquals("{\"note\":{\"text\":\"hello\",\"count\":3}}", json.toString());
    }

    @Test
    void anElementFromAnIncludeWithoutTargetNamespaceIsNotDeclaredWithoutNamespace() {
        assertThrows(UndeclaredElementException.class, () -> review.toJson(new InputSource(new StringReader(
                "<note><text>hello</text><count>3</count></note>"))));
    }

    @Test
    void anElementFromAnIncludeWithoutTargetNamespaceIsWrittenInTheIncludingNamespace() throws Exception {
        final var xml = new String(review.toXml("{\"note\": {\"text\": \"hi\", \"count\": 1}}"), UTF_8);

        final var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final var root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        assertEquals("https://example.com/review", root.getNamespaceURI(), xml);
        assertEquals("hi", root.getElementsByTagNameNS("https://example.com/review", "text").item(0).getTextContent(), xml);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/catalog", "https://example.com/review"})
    void anIncludeWithoutTargetNamespaceFromTwoNamespacesDeclaresItsElementsInBoth(String namespace) throws Exception {
        final var json = new ObjectMapper().readTree(catalog.toJson(new InputSource(new StringReader("""
                <note xmlns="%s"><text>hello</text><count>3</count></note>""".formatted(namespace)))));

        assertEquals("{\"{%s}note\":{\"text\":\"hello\",\"count\":3}}".formatted(namespace), json.toString());
    }

    @Test
    void anImportedSchemaContributesItsGlobalElements() {
        assertEquals(2, notes.size());
    }
}
