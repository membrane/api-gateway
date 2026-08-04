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

import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class XsdToSchemaTest {

    static final String NS = "https://test.example.com";

    static XsdToSchema converterFor(String xsdDeclarations) {
        return converterForSchemas(Map.of(NS, xsdDeclarations));
    }

    static XsdToSchema converterForSchemas(Map<String, String> xsdContentByNamespace) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var map = new LinkedHashMap<String, List<Element>>();
            for (var entry : xsdContentByNamespace.entrySet()) {
                var ns = entry.getKey();
                var xml = """
                        <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                    xmlns:tns="%s"
                                    targetNamespace="%s">
                          %s
                        </xsd:schema>
                        """.formatted(ns, ns, entry.getValue());
                var doc = builder.parse(new InputSource(new StringReader(xml)));
                map.put(ns, List.of(doc.getDocumentElement()));
            }
            return new XsdToSchema(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Schema<?> convert(XsdToSchema converter, String elementName) {
        return converter.convert(new QName(NS, elementName));
    }

    static Schema<?> fieldOf(Schema<?> schema, String name) {
        return schema.getProperties().get(name);
    }

    static boolean isRequired(Schema<?> schema, String name) {
        var req = schema.getRequired();
        return req != null && req.contains(name);
    }

    // ── Unresolvable inputs ───────────────────────────────────────────────

    @Test
    void unknownNamespaceProducesEmptyObjectSchema() {
        var converter = converterFor("<xsd:element name='x' type='xsd:string'/>");

        var schema = converter.convert(new QName("https://unknown.example.com", "x"));

        assertInstanceOf(ObjectSchema.class, schema);
        assertNull(schema.getProperties());
    }

    @Test
    void unknownElementInKnownNamespaceProducesEmptyObjectSchema() {
        var converter = converterFor("<xsd:element name='x' type='xsd:string'/>");

        var schema = convert(converter, "doesNotExist");

        assertInstanceOf(ObjectSchema.class, schema);
        assertNull(schema.getProperties());
    }

    // ── Primitive XSD type mappings ───────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void primitiveTypeMapping(String xsdType, Class<?> expectedSchemaClass) {
        var schema = convert(converterFor("""
                <xsd:element name="root">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="value" type="%s"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """.formatted(xsdType)), "root");

        assertInstanceOf(expectedSchemaClass, fieldOf(schema, "value"));
    }

    static Stream<Arguments> primitiveTypeMapping() {
        return Stream.of(
                arguments("xsd:string",          StringSchema.class),
                arguments("xsd:normalizedString", StringSchema.class),
                arguments("xsd:anyURI",           StringSchema.class),
                arguments("xsd:date",             StringSchema.class),
                arguments("xsd:dateTime",         StringSchema.class),
                arguments("xsd:base64Binary",     StringSchema.class),
                arguments("xsd:integer",          IntegerSchema.class),
                arguments("xsd:int",              IntegerSchema.class),
                arguments("xsd:long",             IntegerSchema.class),
                arguments("xsd:short",            IntegerSchema.class),
                arguments("xsd:decimal",          NumberSchema.class),
                arguments("xsd:float",            NumberSchema.class),
                arguments("xsd:double",           NumberSchema.class),
                arguments("xsd:boolean",          BooleanSchema.class)
        );
    }

    // ── Required and optional fields (minOccurs) ──────────────────────────

    @Test
    void elementWithoutMinOccursIsRequired() {
        var schema = convert(converterFor("""
                <xsd:element name="request">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="username" type="xsd:string"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "request");

        assertTrue(isRequired(schema, "username"));
    }

    @Test
    void elementWithMinOccursZeroIsNotRequired() {
        var schema = convert(converterFor("""
                <xsd:element name="request">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="comment" type="xsd:string" minOccurs="0"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "request");

        assertNotNull(fieldOf(schema, "comment"));
        assertFalse(isRequired(schema, "comment"));
    }

    @Test
    void onlyElementsWithoutMinOccursZeroAreRequired() {
        var schema = convert(converterFor("""
                <xsd:element name="order">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="productId" type="xsd:string"/>
                    <xsd:element name="quantity"  type="xsd:int"/>
                    <xsd:element name="note"      type="xsd:string" minOccurs="0"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "order");

        assertTrue(isRequired(schema, "productId"));
        assertTrue(isRequired(schema, "quantity"));
        assertFalse(isRequired(schema, "note"));
    }

    // ── maxOccurs → ArraySchema ───────────────────────────────────────────

    @Test
    void maxOccursUnboundedProducesArraySchema() {
        var schema = convert(converterFor("""
                <xsd:element name="list">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="item" type="xsd:string" maxOccurs="unbounded"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "list");

        assertInstanceOf(ArraySchema.class, fieldOf(schema, "item"));
    }

    @Test
    void maxOccursGreaterThanOneProducesArraySchema() {
        var schema = convert(converterFor("""
                <xsd:element name="list">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="item" type="xsd:string" maxOccurs="3"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "list");

        assertInstanceOf(ArraySchema.class, fieldOf(schema, "item"));
    }

    @Test
    void maxOccursOneDoesNotProduceArraySchema() {
        var schema = convert(converterFor("""
                <xsd:element name="list">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="item" type="xsd:string" maxOccurs="1"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "list");

        assertFalse(fieldOf(schema, "item") instanceof ArraySchema);
    }

    // ── xsd:all treated same as xsd:sequence ─────────────────────────────

    @Test
    void xsdAllProducesObjectSchemaWithAllFields() {
        var schema = convert(converterFor("""
                <xsd:element name="record">
                  <xsd:complexType>
                    <xsd:all>
                      <xsd:element name="firstName" type="xsd:string"/>
                      <xsd:element name="lastName"  type="xsd:string"/>
                    </xsd:all>
                  </xsd:complexType>
                </xsd:element>
                """), "record");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "firstName"));
        assertInstanceOf(StringSchema.class, fieldOf(schema, "lastName"));
    }

    // ── xsd:choice ────────────────────────────────────────────────────────

    @Test
    void choiceAlternativesAreExposedAsProperties() {
        var schema = convert(converterFor("""
                <xsd:element name="searchRequest">
                  <xsd:complexType><xsd:sequence>
                    <xsd:choice>
                      <xsd:element name="byName" type="xsd:string"/>
                      <xsd:element name="byId"   type="xsd:int"/>
                    </xsd:choice>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "searchRequest");

        assertNotNull(fieldOf(schema, "byName"));
        assertNotNull(fieldOf(schema, "byId"));
    }

    @Test
    void choiceAlternativesAreNotRequired() {
        var schema = convert(converterFor("""
                <xsd:element name="searchRequest">
                  <xsd:complexType><xsd:sequence>
                    <xsd:choice>
                      <xsd:element name="byName" type="xsd:string"/>
                      <xsd:element name="byId"   type="xsd:int"/>
                    </xsd:choice>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "searchRequest");

        assertFalse(isRequired(schema, "byName"));
        assertFalse(isRequired(schema, "byId"));
    }

    // ── xsd:extension — field inheritance ────────────────────────────────

    @Test
    void extensionInheritsBaseTypeFieldsAndAddsOwnFields() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Vehicle">
                  <xsd:sequence>
                    <xsd:element name="brand" type="xsd:string"/>
                    <xsd:element name="year"  type="xsd:int"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="Car">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Vehicle">
                      <xsd:sequence>
                        <xsd:element name="doors" type="xsd:int"/>
                      </xsd:sequence>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="car" type="tns:Car"/>
                """), "car");

        assertInstanceOf(StringSchema.class,  fieldOf(schema, "brand"));  // from Vehicle
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "year"));   // from Vehicle
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "doors"));  // from Car
    }

    // ── xsd:restriction on complexContent ────────────────────────────────

    @Test
    void complexContentRestrictionExposesBaseTypeFields() {
        var schema = convert(converterFor("""
                <xsd:complexType name="FullAddress">
                  <xsd:sequence>
                    <xsd:element name="street"  type="xsd:string"/>
                    <xsd:element name="city"    type="xsd:string"/>
                    <xsd:element name="country" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="ShortAddress">
                  <xsd:complexContent>
                    <xsd:restriction base="tns:FullAddress">
                      <xsd:sequence>
                        <xsd:element name="city"    type="xsd:string"/>
                        <xsd:element name="country" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:restriction>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="address" type="tns:ShortAddress"/>
                """), "address");

        assertNotNull(fieldOf(schema, "street"));
        assertNotNull(fieldOf(schema, "city"));
        assertNotNull(fieldOf(schema, "country"));
    }

    // ── Named xsd:simpleType restriction ──────────────────────────────────

    @Test
    void namedSimpleTypeRestrictionResolvesToBasePrimitive() {
        var schema = convert(converterFor("""
                <xsd:simpleType name="StatusCode">
                  <xsd:restriction base="xsd:string">
                    <xsd:enumeration value="ACTIVE"/>
                    <xsd:enumeration value="INACTIVE"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="status" type="tns:StatusCode"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "status"));
    }

    // ── xsd:simpleContent ─────────────────────────────────────────────────

    @Test
    void simpleContentProducesStringSchema() {
        var schema = convert(converterFor("""
                <xsd:element name="note">
                  <xsd:complexType>
                    <xsd:simpleContent>
                      <xsd:extension base="xsd:string"/>
                    </xsd:simpleContent>
                  </xsd:complexType>
                </xsd:element>
                """), "note");

        assertInstanceOf(StringSchema.class, schema);
    }

    // ── Self-referencing types ────────────────────────────────────────────

    @Test
    void selfReferencingTypeCompletesWithoutStackOverflow() {
        assertDoesNotThrow(() -> convert(converterFor("""
                <xsd:complexType name="TreeNode">
                  <xsd:sequence>
                    <xsd:element name="value"    type="xsd:string"/>
                    <xsd:element name="children" type="tns:TreeNode" minOccurs="0" maxOccurs="unbounded"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:element name="tree" type="tns:TreeNode"/>
                """), "tree"));
    }

    @Test
    void selfReferencingTypePreservesNonRecursiveFields() {
        var schema = convert(converterFor("""
                <xsd:complexType name="TreeNode">
                  <xsd:sequence>
                    <xsd:element name="value"    type="xsd:string"/>
                    <xsd:element name="children" type="tns:TreeNode" minOccurs="0" maxOccurs="unbounded"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:element name="tree" type="tns:TreeNode"/>
                """), "tree");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "value"));
    }

    @Test
    void recursiveReferenceBreaksCycleWithEmptyObjectSchema() {
        var schema = convert(converterFor("""
                <xsd:complexType name="TreeNode">
                  <xsd:sequence>
                    <xsd:element name="value"    type="xsd:string"/>
                    <xsd:element name="children" type="tns:TreeNode" minOccurs="0" maxOccurs="unbounded"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:element name="tree" type="tns:TreeNode"/>
                """), "tree");

        var children = (ArraySchema) fieldOf(schema, "children");
        assertInstanceOf(ObjectSchema.class, children.getItems());
        assertNull(children.getItems().getProperties());
    }

    // ── xsd:attribute mapping ─────────────────────────────────────────────

    @Test
    void attributeIsMappedToAtPrefixedProperty() {
        var schema = convert(converterFor("""
                <xsd:element name="record">
                  <xsd:complexType>
                    <xsd:sequence>
                      <xsd:element name="name" type="xsd:string"/>
                    </xsd:sequence>
                    <xsd:attribute name="id" type="xsd:string"/>
                  </xsd:complexType>
                </xsd:element>
                """), "record");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "@id"));
        assertFalse(isRequired(schema, "@id"));
    }

    @Test
    void requiredAttributeUseRequiredIsMarkedRequired() {
        var schema = convert(converterFor("""
                <xsd:element name="record">
                  <xsd:complexType>
                    <xsd:sequence>
                      <xsd:element name="name" type="xsd:string"/>
                    </xsd:sequence>
                    <xsd:attribute name="id" type="xsd:string" use="required"/>
                  </xsd:complexType>
                </xsd:element>
                """), "record");

        assertTrue(isRequired(schema, "@id"));
    }

    @Test
    void attributeWithInlineSimpleTypeIsMapped() {
        var schema = convert(converterFor("""
                <xsd:element name="record">
                  <xsd:complexType>
                    <xsd:sequence>
                      <xsd:element name="name" type="xsd:string"/>
                    </xsd:sequence>
                    <xsd:attribute name="status">
                      <xsd:simpleType>
                        <xsd:restriction base="xsd:string">
                          <xsd:enumeration value="ACTIVE"/>
                          <xsd:enumeration value="INACTIVE"/>
                        </xsd:restriction>
                      </xsd:simpleType>
                    </xsd:attribute>
                  </xsd:complexType>
                </xsd:element>
                """), "record");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "@status"));
    }

    @Test
    void attributeDeclaredOnExtensionIsMapped() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Vehicle">
                  <xsd:sequence>
                    <xsd:element name="brand" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="Car">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Vehicle">
                      <xsd:sequence>
                        <xsd:element name="doors" type="xsd:int"/>
                      </xsd:sequence>
                      <xsd:attribute name="id" type="xsd:string"/>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="car" type="tns:Car"/>
                """), "car");

        assertInstanceOf(StringSchema.class,  fieldOf(schema, "brand"));  // from Vehicle
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "doors"));  // from Car
        assertInstanceOf(StringSchema.class,  fieldOf(schema, "@id"));    // from Car's extension
    }

    // ── xsd:element ref= resolution ───────────────────────────────────────

    @Test
    void elementRefInSameNamespaceIsResolved() {
        var schema = convert(converterFor("""
                <xsd:element name="city" type="xsd:string"/>
                <xsd:element name="wrapper">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element ref="tns:city"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "wrapper");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "city"));
    }

    @Test
    void elementRefInDifferentNamespaceIsResolved() {
        var converter = converterForSchemas(Map.of(
                NS, """
                        <xsd:import namespace="https://other.example.com"/>
                        <xsd:element name="wrapper">
                          <xsd:complexType><xsd:sequence>
                            <xsd:element ref="other:bar" xmlns:other="https://other.example.com"/>
                          </xsd:sequence></xsd:complexType>
                        </xsd:element>
                        """,
                "https://other.example.com", """
                        <xsd:element name="bar" type="xsd:int"/>
                        """
        ));

        var schema = convert(converter, "wrapper");
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "bar"));
    }

    @Test
    void attributeRefInComplexTypeIsSkipped() {
        // xsd:attribute ref= is not supported — the referenced attribute is silently omitted
        var schema = convert(converterFor("""
                <xsd:element name="record">
                  <xsd:complexType>
                    <xsd:attribute ref="tns:globalId"/>
                  </xsd:complexType>
                </xsd:element>
                """), "record");

        assertNull(schema.getProperties());
    }

    // ── Duplicate local-name collision from two namespaces ─────────────────

    @Test
    void sequenceWithSameLocalNameFromTwoNamespacesLastOneWins() {
        // Documents current "last writer wins" collision — both refs resolve to property "value";
        // the second ref (ns2:value = xsd:int) overwrites the first (ns1:value = xsd:string).
        // TODO: future work could use "{ns}value"-keyed properties to distinguish namespaces.
        final String NS1 = "https://ns1.example.com";
        final String NS2 = "https://ns2.example.com";
        final String NS3 = "https://ns3.example.com";

        var schemas = new java.util.LinkedHashMap<String, String>();
        schemas.put(NS1, "<xsd:element name=\"value\" type=\"xsd:string\"/>");
        schemas.put(NS2, "<xsd:element name=\"value\" type=\"xsd:int\"/>");
        schemas.put(NS3, """
                <xsd:element name="container">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element ref="ns1:value" xmlns:ns1="https://ns1.example.com"/>
                    <xsd:element ref="ns2:value" xmlns:ns2="https://ns2.example.com"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        var converter = converterForSchemas(schemas);

        var schema = converter.convert(new QName(NS3, "container"));

        // Both refs produce a property named "value"; second write wins
        assertNotNull(fieldOf(schema, "value"));
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "value")); // ns2:value = xsd:int
    }

    // ── Cross-namespace type resolution ───────────────────────────────────

    @Test
    void typeDefinedInDifferentNamespaceSchemaIsResolved() {
        var converter = converterForSchemas(Map.of(
                NS, """
                        <xsd:import namespace="https://types.example.com"/>
                        <xsd:element name="request">
                          <xsd:complexType><xsd:sequence>
                            <xsd:element name="payload" type="types:Payload"
                                         xmlns:types="https://types.example.com"/>
                          </xsd:sequence></xsd:complexType>
                        </xsd:element>
                        """,
                "https://types.example.com", """
                        <xsd:complexType name="Payload">
                          <xsd:sequence>
                            <xsd:element name="body" type="xsd:string"/>
                          </xsd:sequence>
                        </xsd:complexType>
                        """
        ));

        var payload = fieldOf(convert(converter, "request"), "payload");

        assertInstanceOf(ObjectSchema.class, payload);
        assertInstanceOf(StringSchema.class, fieldOf(payload, "body"));
    }
}
