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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.qualifiedKey;
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

    /** A field whose type is a named one, resolved through the component it references. */
    static Schema<?> fieldOf(XsdToSchema converter, Schema<?> schema, String name) {
        return deref(converter, fieldOf(schema, name));
    }

    /** The component {@code schema} references, or {@code schema} itself if it references none. */
    static Schema<?> deref(XsdToSchema converter, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) return schema;
        String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
        Schema<?> component = converter.getComponents().get(name);
        assertNotNull(component, "dangling reference " + schema.get$ref());
        return component;
    }

    static void assertRefTo(String componentName, Schema<?> schema) {
        assertEquals("#/components/schemas/" + componentName, schema.get$ref());
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
    void primitiveTypeMapping(String xsdType, Class<?> expectedSchemaClass, String expectedFormat, String expectedDescription) {
        var schema = convert(converterFor("""
                <xsd:element name="root">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="value" type="%s"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """.formatted(xsdType)), "root");

        var value = fieldOf(schema, "value");
        assertInstanceOf(expectedSchemaClass, value);
        assertEquals(expectedFormat, value.getFormat());
        assertEquals(expectedDescription, value.getDescription());
    }

    static Stream<Arguments> primitiveTypeMapping() {
        return Stream.of(
                arguments("xsd:string",          StringSchema.class,  null,        null),
                arguments("xsd:normalizedString", StringSchema.class, null,        "xsd:normalizedString"),
                arguments("xsd:anyURI",           StringSchema.class, null,        "xsd:anyURI"),
                arguments("xsd:date",             StringSchema.class, "date",      null),
                arguments("xsd:dateTime",         StringSchema.class, "date-time", null),
                arguments("xsd:base64Binary",     StringSchema.class, "byte",      null),
                arguments("xsd:hexBinary",        StringSchema.class, "binary",    null),
                arguments("xsd:duration",         StringSchema.class, null,        "xsd:duration"),
                arguments("xsd:integer",          IntegerSchema.class, null,       "xsd:integer"),
                arguments("xsd:int",              IntegerSchema.class, "int32",    null),
                arguments("xsd:long",             IntegerSchema.class, "int64",    null),
                arguments("xsd:short",            IntegerSchema.class, null,       "xsd:short"),
                arguments("xsd:unsignedShort",    IntegerSchema.class, null,       "xsd:unsignedShort"),
                arguments("xsd:decimal",          NumberSchema.class,  null,       "xsd:decimal"),
                arguments("xsd:float",            NumberSchema.class,  "float",    null),
                arguments("xsd:double",           NumberSchema.class,  "double",   null),
                arguments("xsd:boolean",          BooleanSchema.class, null,       null)
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

    @Test
    void repeatedNamedChoiceAlternativeProducesArraySchema() {
        var schema = convert(converterFor("""
                <xsd:element name="entry">
                  <xsd:complexType><xsd:choice>
                    <xsd:element name="tag"  type="xsd:string" maxOccurs="unbounded"/>
                    <xsd:element name="note" type="xsd:string"/>
                  </xsd:choice></xsd:complexType>
                </xsd:element>
                """), "entry");

        var tag = assertInstanceOf(ArraySchema.class, fieldOf(schema, "tag"));
        assertInstanceOf(StringSchema.class, tag.getItems());
        assertFalse(fieldOf(schema, "note") instanceof ArraySchema, "the single-occurrence alternative stays scalar");
        assertFalse(isRequired(schema, "tag"), "array wrapping must not make an alternative required");
    }

    @Test
    void namedChoiceAlternativeWithNumericMaxOccursProducesArraySchema() {
        var schema = convert(converterFor("""
                <xsd:element name="entry">
                  <xsd:complexType><xsd:choice>
                    <xsd:element name="tag"  type="xsd:string" maxOccurs="3"/>
                    <xsd:element name="note" type="xsd:string"/>
                  </xsd:choice></xsd:complexType>
                </xsd:element>
                """), "entry");

        assertInstanceOf(ArraySchema.class, fieldOf(schema, "tag"));
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

    @Test
    void sequenceNestedInsideChoiceExposesItsElements() {
        var schema = convert(converterFor("""
                <xsd:element name="payload">
                  <xsd:complexType><xsd:choice>
                    <xsd:element name="scalar" type="xsd:string"/>
                    <xsd:sequence>
                      <xsd:element name="part1" type="xsd:string"/>
                      <xsd:element name="part2" type="xsd:int"/>
                    </xsd:sequence>
                  </xsd:choice></xsd:complexType>
                </xsd:element>
                """), "payload");

        assertNotNull(fieldOf(schema, "scalar"), "direct choice element must be present");
        assertNotNull(fieldOf(schema, "part1"),  "sequence-nested element part1 must be present");
        assertNotNull(fieldOf(schema, "part2"),  "sequence-nested element part2 must be present");
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "part2"));
        // the "scalar" alternative may be the one chosen, so the sequence branch cannot be required
        assertFalse(isRequired(schema, "part1"), "sequence-nested alternative must stay optional");
        assertFalse(isRequired(schema, "part2"), "sequence-nested alternative must stay optional");
    }

    @Test
    void groupAlternativeInsideChoiceExposesItsFieldsAsOptional() {
        var schema = convert(converterFor("""
                <xsd:group name="AddressGroup">
                  <xsd:sequence>
                    <xsd:element name="street" type="xsd:string"/>
                    <xsd:element name="city"   type="xsd:string"/>
                  </xsd:sequence>
                </xsd:group>

                <xsd:element name="contact">
                  <xsd:complexType><xsd:choice>
                    <xsd:element name="email" type="xsd:string"/>
                    <xsd:group ref="tns:AddressGroup"/>
                  </xsd:choice></xsd:complexType>
                </xsd:element>
                """), "contact");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "email"));
        assertInstanceOf(StringSchema.class, fieldOf(schema, "street"), "group alternative must not be dropped");
        assertInstanceOf(StringSchema.class, fieldOf(schema, "city"),   "group alternative must not be dropped");
        assertFalse(isRequired(schema, "street"), "group alternative must stay optional");
        assertFalse(isRequired(schema, "city"),   "group alternative must stay optional");
    }

    @Test
    void sequenceInsideChoiceInsideExtensionStaysOptional() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Notification">
                  <xsd:sequence>
                    <xsd:element name="subject" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="RoutedNotification">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Notification">
                      <xsd:choice>
                        <xsd:element name="broadcast" type="xsd:boolean"/>
                        <xsd:sequence>
                          <xsd:element name="recipient" type="xsd:string"/>
                          <xsd:element name="priority"  type="xsd:int"/>
                        </xsd:sequence>
                      </xsd:choice>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="notification" type="tns:RoutedNotification"/>
                """), "notification");

        assertNotNull(fieldOf(schema, "subject"),   "inherited base field");
        assertNotNull(fieldOf(schema, "recipient"), "sequence branch of a choice in an extension");
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "priority"));
        assertTrue(isRequired(schema, "subject"),    "a plain base field stays required");
        assertFalse(isRequired(schema, "recipient"), "choice branch must stay optional");
        assertFalse(isRequired(schema, "priority"),  "choice branch must stay optional");
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
    void complexContentRestrictionExposesOnlyItsOwnFields() {
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

        assertNull(fieldOf(schema, "street"),
                "the restriction leaves street out, so it is not part of ShortAddress");
        assertNotNull(fieldOf(schema, "city"));
        assertNotNull(fieldOf(schema, "country"));
    }

    @Test
    void complexContentRestrictionKeepsItsOwnAttributes() {
        var schema = convert(converterFor("""
                <xsd:complexType name="FullAddress">
                  <xsd:sequence>
                    <xsd:element name="street" type="xsd:string"/>
                    <xsd:element name="city"   type="xsd:string"/>
                  </xsd:sequence>
                  <xsd:attribute name="kind" type="xsd:string"/>
                </xsd:complexType>

                <xsd:complexType name="ShortAddress">
                  <xsd:complexContent>
                    <xsd:restriction base="tns:FullAddress">
                      <xsd:sequence>
                        <xsd:element name="city" type="xsd:string"/>
                      </xsd:sequence>
                      <xsd:attribute name="kind" type="xsd:string" use="required"/>
                    </xsd:restriction>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="address" type="tns:ShortAddress"/>
                """), "address");

        assertNotNull(fieldOf(schema, "@kind"), "an attribute the restriction re-declares is kept");
        assertTrue(isRequired(schema, "@kind"), "and its own use=required applies");
        assertNull(fieldOf(schema, "street"));
    }

    @Test
    void complexContentExtensionStillInheritsBaseTypeFields() {
        var schema = convert(converterFor("""
                <xsd:complexType name="BaseAddress">
                  <xsd:sequence>
                    <xsd:element name="street" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="FullAddress">
                  <xsd:complexContent>
                    <xsd:extension base="tns:BaseAddress">
                      <xsd:sequence>
                        <xsd:element name="city" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="address" type="tns:FullAddress"/>
                """), "address");

        assertNotNull(fieldOf(schema, "street"), "an extension does inherit, unlike a restriction");
        assertNotNull(fieldOf(schema, "city"));
    }

    // ── xsd:group in particle position ────────────────────────────────────

    @Test
    void groupRefAsSoleParticleOfComplexTypeExposesGroupFields() {
        var schema = convert(converterFor("""
                <xsd:group name="NameGroup">
                  <xsd:sequence>
                    <xsd:element name="firstName" type="xsd:string"/>
                    <xsd:element name="lastName"  type="xsd:string"/>
                  </xsd:sequence>
                </xsd:group>

                <xsd:element name="person">
                  <xsd:complexType>
                    <xsd:group ref="tns:NameGroup"/>
                    <xsd:attribute name="id" type="xsd:int"/>
                  </xsd:complexType>
                </xsd:element>
                """), "person");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "firstName"));
        assertInstanceOf(StringSchema.class, fieldOf(schema, "lastName"));
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "@id"), "attributes must survive the group particle");
    }

    @Test
    void groupRefInsideExtensionExposesBaseAndGroupFields() {
        var schema = convert(converterFor("""
                <xsd:group name="AuditGroup">
                  <xsd:sequence>
                    <xsd:element name="createdBy" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:group>

                <xsd:complexType name="Document">
                  <xsd:sequence>
                    <xsd:element name="title" type="xsd:string"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="AuditedDocument">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Document">
                      <xsd:group ref="tns:AuditGroup"/>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="document" type="tns:AuditedDocument"/>
                """), "document");

        assertInstanceOf(StringSchema.class, fieldOf(schema, "title"),     "inherited base field");
        assertInstanceOf(StringSchema.class, fieldOf(schema, "createdBy"), "field contributed by the group");
    }

    @Test
    void mutuallyRecursiveGroupsCompleteWithoutStackOverflow() {
        var schema = convert(converterFor("""
                <xsd:group name="NodeGroup">
                  <xsd:sequence>
                    <xsd:element name="label" type="xsd:string"/>
                    <xsd:group ref="tns:BranchGroup"/>
                  </xsd:sequence>
                </xsd:group>

                <xsd:group name="BranchGroup">
                  <xsd:sequence>
                    <xsd:element name="depth" type="xsd:int"/>
                    <xsd:group ref="tns:NodeGroup"/>
                  </xsd:sequence>
                </xsd:group>

                <xsd:element name="tree">
                  <xsd:complexType>
                    <xsd:group ref="tns:NodeGroup"/>
                  </xsd:complexType>
                </xsd:element>
                """), "tree");

        assertInstanceOf(StringSchema.class,  fieldOf(schema, "label"), "fields before the cycle must survive");
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "depth"), "one level of the cycle is expanded");
    }

    @Test
    void choiceInsideExtensionExposesAlternativesAsOptionalFields() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Payment">
                  <xsd:sequence>
                    <xsd:element name="amount" type="xsd:decimal"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:complexType name="CardOrTransferPayment">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Payment">
                      <xsd:choice>
                        <xsd:element name="cardNumber" type="xsd:string"/>
                        <xsd:element name="iban"       type="xsd:string"/>
                      </xsd:choice>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="payment" type="tns:CardOrTransferPayment"/>
                """), "payment");

        assertNotNull(fieldOf(schema, "amount"),     "inherited base field");
        assertNotNull(fieldOf(schema, "cardNumber"), "choice alternative in an extension");
        assertNotNull(fieldOf(schema, "iban"),       "choice alternative in an extension");
        assertFalse(isRequired(schema, "cardNumber"), "a choice alternative must stay optional");
        assertFalse(isRequired(schema, "iban"),       "a choice alternative must stay optional");
    }

    // ── Named xsd:simpleType restriction ──────────────────────────────────

    @Test
    void namedSimpleTypeRestrictionResolvesToBasePrimitive() {
        var converter = converterFor("""
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
                """);
                var schema = convert(converter, "account");

        assertInstanceOf(StringSchema.class, fieldOf(converter, schema, "status"));
        assertEquals(List.of("ACTIVE", "INACTIVE"), fieldOf(converter, schema, "status").getEnum());
    }

    @Test
    void namedSimpleTypePatternPropagatedToSchema() {
        var converter = converterFor("""
                <xsd:simpleType name="PhoneNumber">
                  <xsd:restriction base="xsd:string">
                    <xsd:pattern value="\\+?[0-9]{7,15}"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="contact">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="phoneNumber" type="tns:PhoneNumber"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "contact");

        Schema<?> phoneSchema = fieldOf(converter, schema, "phoneNumber");
        assertInstanceOf(StringSchema.class, phoneSchema);
        assertEquals("^(?:\\+?[0-9]{7,15})$", phoneSchema.getPattern(),
                "an XSD pattern matches the whole value, a JSON Schema one matches anywhere");
    }

    @Test
    void severalPatternFacetsBecomeAlternatives() {
        var converter = converterFor("""
                <xsd:simpleType name="Reference">
                  <xsd:restriction base="xsd:string">
                    <xsd:pattern value="[A-Z]{3}"/>
                    <xsd:pattern value="[0-9]{4}"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="doc">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="ref" type="tns:Reference"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "doc");

        assertEquals("^(?:[A-Z]{3}|[0-9]{4})$", fieldOf(converter, schema, "ref").getPattern(),
                "in XSD the facets are alternatives, so keeping only the last one would be wrong");
    }

    @Test
    void enumerationLiteralsCarryTheTypeOfTheirSchema() {
        var converter = converterFor("""
                <xsd:simpleType name="Rating">
                  <xsd:restriction base="xsd:int">
                    <xsd:enumeration value="1"/>
                    <xsd:enumeration value="5"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:simpleType name="Factor">
                  <xsd:restriction base="xsd:decimal">
                    <xsd:enumeration value="1.5"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:simpleType name="Flag">
                  <xsd:restriction base="xsd:boolean">
                    <xsd:enumeration value="1"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="review">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="rating" type="tns:Rating"/>
                    <xsd:element name="factor" type="tns:Factor"/>
                    <xsd:element name="flag"   type="tns:Flag"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "review");

        // Strings here would match no value of an integer, number or boolean field.
        assertEquals(List.of(1, 5), fieldOf(converter, schema, "rating").getEnum());
        assertEquals(List.of(new BigDecimal("1.5")), fieldOf(converter, schema, "factor").getEnum());
        assertEquals(List.of(true), fieldOf(converter, schema, "flag").getEnum());
    }

    @Test
    void enumerationLiteralThatIsNoValueOfTheTypeIsDropped() {
        var converter = converterFor("""
                <xsd:simpleType name="Rating">
                  <xsd:restriction base="xsd:int">
                    <xsd:enumeration value="1"/>
                    <xsd:enumeration value="many"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="review">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="rating" type="tns:Rating"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "review");

        assertEquals(List.of(1), fieldOf(converter, schema, "rating").getEnum(),
                "the unusable literal costs its enum item, not the whole constraint");
    }

    // ── xsd:restriction facets ────────────────────────────────────────────

    @Test
    void lengthFacetsPropagatedToSchema() {
        var converter = converterFor("""
                <xsd:simpleType name="Iban">
                  <xsd:restriction base="xsd:string">
                    <xsd:minLength value="15"/>
                    <xsd:maxLength value="34"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="iban" type="tns:Iban"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "account");

        Schema<?> iban = fieldOf(converter, schema, "iban");
        assertEquals(15, iban.getMinLength());
        assertEquals(34, iban.getMaxLength());
    }

    @Test
    void lengthFacetPinsBothBounds() {
        var converter = converterFor("""
                <xsd:simpleType name="CountryCode">
                  <xsd:restriction base="xsd:string">
                    <xsd:length value="2"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="address">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="country" type="tns:CountryCode"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "address");

        Schema<?> country = fieldOf(converter, schema, "country");
        assertEquals(2, country.getMinLength());
        assertEquals(2, country.getMaxLength());
    }

    @Test
    void inclusiveRangeFacetsPropagatedToSchema() {
        var converter = converterFor("""
                <xsd:simpleType name="Percentage">
                  <xsd:restriction base="xsd:int">
                    <xsd:minInclusive value="0"/>
                    <xsd:maxInclusive value="100"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="rating">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="score" type="tns:Percentage"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "rating");

        Schema<?> score = fieldOf(converter, schema, "score");
        assertEquals(new BigDecimal("0"), score.getMinimum());
        assertEquals(new BigDecimal("100"), score.getMaximum());
        assertNull(score.getExclusiveMinimumValue(), "an inclusive bound must not be marked exclusive");
        assertNull(score.getExclusiveMaximumValue(), "an inclusive bound must not be marked exclusive");
    }

    @Test
    void exclusiveRangeFacetsMarkedExclusive() {
        var converter = converterFor("""
                <xsd:simpleType name="PositiveAmount">
                  <xsd:restriction base="xsd:decimal">
                    <xsd:minExclusive value="0"/>
                    <xsd:maxExclusive value="1000.50"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="payment">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="amount" type="tns:PositiveAmount"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "payment");

        // the generated document is OpenAPI 3.1, where an exclusive bound is its own numeric
        // keyword; the 3.0 minimum-plus-boolean form is silently dropped when serializing 3.1
        Schema<?> amount = fieldOf(converter, schema, "amount");
        assertEquals(new BigDecimal("0"), amount.getExclusiveMinimumValue());
        assertEquals(new BigDecimal("1000.50"), amount.getExclusiveMaximumValue());
        assertNull(amount.getMinimum(), "an exclusive bound must not also be emitted as inclusive");
        assertNull(amount.getMaximum(), "an exclusive bound must not also be emitted as inclusive");
    }

    @Test
    void facetsOnInlineSimpleTypePropagatedToSchema() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="iban">
                      <xsd:simpleType>
                        <xsd:restriction base="xsd:string">
                          <xsd:minLength value="15"/>
                          <xsd:maxLength value="34"/>
                        </xsd:restriction>
                      </xsd:simpleType>
                    </xsd:element>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        Schema<?> iban = fieldOf(schema, "iban");
        assertEquals(15, iban.getMinLength());
        assertEquals(34, iban.getMaxLength());
    }

    @Test
    void unparsableFacetValueIsIgnoredWithoutFailingTheConversion() {
        var converter = converterFor("""
                <xsd:simpleType name="Weird">
                  <xsd:restriction base="xsd:int">
                    <xsd:minInclusive value="abc"/>
                    <xsd:maxInclusive value="100"/>
                  </xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="rating">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="score" type="tns:Weird"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
                var schema = convert(converter, "rating");

        Schema<?> score = fieldOf(converter, schema, "score");
        assertNull(score.getMinimum(), "the unparsable bound is dropped");
        assertEquals(new BigDecimal("100"), score.getMaximum(), "the valid bound still applies");
    }

    // ── nillable ──────────────────────────────────────────────────────────

    @Test
    void nillableElementProducesNullableSchema() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="closedAt" type="xsd:date" nillable="true"/>
                    <xsd:element name="openedAt" type="xsd:date"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        // OpenAPI 3.1 lists "null" among the allowed types; the 3.0 nullable flag is not emitted
        assertEquals(Set.of("string", "null"), fieldOf(schema, "closedAt").getTypes());
        assertEquals(Set.of("string"), fieldOf(schema, "openedAt").getTypes(),
                "a plain element keeps its single type");
    }

    @Test
    void nillableRepeatedElementMarksTheItemsNullableNotTheArray() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="note" type="xsd:string" nillable="true" maxOccurs="unbounded"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        var notes = assertInstanceOf(ArraySchema.class, fieldOf(schema, "note"));
        assertEquals(Set.of("array"), notes.getTypes(), "nillable describes each occurrence, not the list");
        assertEquals(Set.of("string", "null"), notes.getItems().getTypes());
    }

    // ── default= / fixed= ─────────────────────────────────────────────────

    @Test
    void defaultValueOnStringElementKeptAsString() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="currency" type="xsd:string" default="EUR"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        assertEquals("EUR", fieldOf(schema, "currency").getDefault());
    }

    @Test
    void defaultValueOnNumericElementCoercedToNumber() {
        var schema = convert(converterFor("""
                <xsd:element name="paging">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="pageSize" type="xsd:int" default="25"/>
                    <xsd:element name="factor"   type="xsd:decimal" default="1.5"/>
                    <xsd:element name="verbose"  type="xsd:boolean" default="true"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "paging");

        assertEquals(25, fieldOf(schema, "pageSize").getDefault());
        assertEquals(new BigDecimal("1.5"), fieldOf(schema, "factor").getDefault());
        assertEquals(true, fieldOf(schema, "verbose").getDefault());
    }

    @Test
    void numericBooleanDefaultsKeepTheirMeaning() {
        // 1 and 0 are legal xsd:boolean literals; read naively they would both come out false.
        var schema = convert(converterFor("""
                <xsd:element name="flags">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="on"  type="xsd:boolean" default="1"/>
                    <xsd:element name="off" type="xsd:boolean" default="0"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "flags");

        assertEquals(true, fieldOf(schema, "on").getDefault());
        assertEquals(false, fieldOf(schema, "off").getDefault());
    }

    @Test
    void booleanDefaultThatIsNoBooleanLiteralIsDropped() {
        var schema = convert(converterFor("""
                <xsd:element name="flags">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="on" type="xsd:boolean" fixed="maybe"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "flags");

        var field = fieldOf(schema, "on");
        assertNull(field.getDefault(), "an invalid literal must not be emitted as false");
        assertNull(field.getEnum(), "and must not restrict the field to a made-up value");
    }

    @Test
    void defaultValueThatDoesNotFitTheTypeIsDropped() {
        var schema = convert(converterFor("""
                <xsd:element name="paging">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="pageSize" type="xsd:int" default="many"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "paging");

        assertNull(fieldOf(schema, "pageSize").getDefault(),
                "a default that is not a valid value for the type must not reach the document");
    }

    @Test
    void fixedValueBecomesBothDefaultAndSingleValueEnum() {
        var schema = convert(converterFor("""
                <xsd:element name="envelope">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="version" type="xsd:string" fixed="1.0"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "envelope");

        Schema<?> version = fieldOf(schema, "version");
        assertEquals("1.0", version.getDefault(), "the value is still documented as the default");
        assertEquals(List.of("1.0"), version.getEnum(), "fixed also restricts which values are valid");
    }

    @Test
    void fixedEnumItemCarriesTheSchemaType() {
        var schema = convert(converterFor("""
                <xsd:element name="envelope">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="revision" type="xsd:int" fixed="3"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "envelope");

        Schema<?> revision = fieldOf(schema, "revision");
        assertEquals(List.of(3), revision.getEnum(), "the enum item must be a number, not a quoted string");
        assertEquals(3, revision.getDefault());
    }

    @Test
    void plainDefaultDoesNotRestrictValues() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="currency" type="xsd:string" default="EUR"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "account");

        assertNull(fieldOf(schema, "currency").getEnum(),
                "a default is only a suggestion, so it must not become an enum");
    }

    @Test
    void fixedValueThatDoesNotFitTheTypeYieldsNoEnum() {
        var schema = convert(converterFor("""
                <xsd:element name="envelope">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="revision" type="xsd:int" fixed="three"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """), "envelope");

        Schema<?> revision = fieldOf(schema, "revision");
        assertNull(revision.getDefault());
        assertNull(revision.getEnum(), "an unusable literal must not reach the document in either form");
    }

    @Test
    void defaultValueOnAttributePropagatedToSchema() {
        var schema = convert(converterFor("""
                <xsd:element name="account">
                  <xsd:complexType>
                    <xsd:sequence>
                      <xsd:element name="iban" type="xsd:string"/>
                    </xsd:sequence>
                    <xsd:attribute name="currency" type="xsd:string" default="EUR"/>
                  </xsd:complexType>
                </xsd:element>
                """), "account");

        assertEquals("EUR", fieldOf(schema, "@currency").getDefault());
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

    @Test
    void simpleContentWithoutAttributesTakesItsBaseType() {
        var schema = convert(converterFor("""
                <xsd:element name="amount">
                  <xsd:complexType>
                    <xsd:simpleContent>
                      <xsd:extension base="xsd:decimal"/>
                    </xsd:simpleContent>
                  </xsd:complexType>
                </xsd:element>
                """), "amount");

        assertInstanceOf(NumberSchema.class, schema,
                "the value type must survive, rather than degrading to string");
    }

    @Test
    void simpleContentWithAttributesBecomesObjectWithValueProperty() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Money">
                  <xsd:simpleContent>
                    <xsd:extension base="xsd:decimal">
                      <xsd:attribute name="currency" type="xsd:string" use="required"/>
                    </xsd:extension>
                  </xsd:simpleContent>
                </xsd:complexType>

                <xsd:element name="price" type="tns:Money"/>
                """), "price");

        assertInstanceOf(ObjectSchema.class, schema);
        assertInstanceOf(NumberSchema.class, fieldOf(schema, "$value"), "the text value keeps the base type");
        assertInstanceOf(StringSchema.class, fieldOf(schema, "@currency"), "the attribute is kept alongside it");
        assertTrue(isRequired(schema, "$value"), "an element always carries its own value");
        assertTrue(isRequired(schema, "@currency"), "use=required is still honoured");
    }

    @Test
    void simpleContentRestrictionAppliesItsFacetsToTheValue() {
        var schema = convert(converterFor("""
                <xsd:complexType name="Code">
                  <xsd:simpleContent>
                    <xsd:restriction base="xsd:string">
                      <xsd:maxLength value="3"/>
                      <xsd:attribute name="scheme" type="xsd:string"/>
                    </xsd:restriction>
                  </xsd:simpleContent>
                </xsd:complexType>

                <xsd:element name="code" type="tns:Code"/>
                """), "code");

        assertEquals(3, fieldOf(schema, "$value").getMaxLength(),
                "a facet on the restriction constrains the value, not the wrapper");
        assertNotNull(fieldOf(schema, "@scheme"));
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
    void recursiveTypeReferencesItselfByRef() {
        var converter = converterFor("""
                <xsd:complexType name="TreeNode">
                  <xsd:sequence>
                    <xsd:element name="value"    type="xsd:string"/>
                    <xsd:element name="children" type="tns:TreeNode" minOccurs="0" maxOccurs="unbounded"/>
                  </xsd:sequence>
                </xsd:complexType>

                <xsd:element name="tree" type="tns:TreeNode"/>
                """);
        var schema = convert(converter, "tree");

        // The cycle is what the component is for: the back edge is a reference to the type itself,
        // so the recursion is expressed rather than truncated to an empty schema.
        var children = (ArraySchema) fieldOf(schema, "children");
        assertRefTo("TreeNode", children.getItems());

        var treeNode = deref(converter, children.getItems());
        assertInstanceOf(StringSchema.class, fieldOf(treeNode, "value"));
        assertRefTo("TreeNode", ((ArraySchema) fieldOf(treeNode, "children")).getItems());
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
        assertEquals(List.of("ACTIVE", "INACTIVE"), fieldOf(schema, "@status").getEnum());
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

    @Test
    void choiceRefInSameNamespaceIsResolved() {
        var schema = convert(converterFor("""
                <xsd:element name="textInput" type="xsd:string"/>
                <xsd:element name="request">
                  <xsd:complexType><xsd:choice>
                    <xsd:element ref="tns:textInput"/>
                    <xsd:element name="numericInput" type="xsd:int"/>
                  </xsd:choice></xsd:complexType>
                </xsd:element>
                """), "request");

        assertInstanceOf(StringSchema.class,  fieldOf(schema, "textInput"));   // resolved from ref
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "numericInput")); // named element still works
    }

    @Test
    void choiceRefInDifferentNamespaceIsResolved() {
        var converter = converterForSchemas(Map.of(
                NS, """
                        <xsd:import namespace="https://types.example.com"/>
                        <xsd:element name="request">
                          <xsd:complexType><xsd:choice>
                            <xsd:element ref="types:remoteInput" xmlns:types="https://types.example.com"/>
                            <xsd:element name="localInput" type="xsd:string"/>
                          </xsd:choice></xsd:complexType>
                        </xsd:element>
                        """,
                "https://types.example.com", """
                        <xsd:element name="remoteInput" type="xsd:int"/>
                        """
        ));

        var schema = convert(converter, "request");
        assertInstanceOf(IntegerSchema.class, fieldOf(schema, "remoteInput")); // resolved cross-NS
        assertInstanceOf(StringSchema.class,  fieldOf(schema, "localInput"));  // named element still works
    }

    @Test
    void choiceRefsWithSameLocalNameInDifferentNamespacesAreQualifiedToAvoidCollision() {
        var converter = converterForSchemas(Map.of(
                NS, """
                        <xsd:import namespace="https://types-a.example.com"/>
                        <xsd:import namespace="https://types-b.example.com"/>
                        <xsd:element name="request">
                          <xsd:complexType><xsd:choice>
                            <xsd:element ref="a:value" xmlns:a="https://types-a.example.com"/>
                            <xsd:element ref="b:value" xmlns:b="https://types-b.example.com"/>
                          </xsd:choice></xsd:complexType>
                        </xsd:element>
                        """,
                "https://types-a.example.com", """
                        <xsd:element name="value" type="xsd:string"/>
                        """,
                "https://types-b.example.com", """
                        <xsd:element name="value" type="xsd:int"/>
                        """
        ));

        var schema = convert(converter, "request");

        // Both choice alternatives are named "value" but come from different namespaces with
        // different types. Since they collide, both are keyed with a namespace-qualified key
        // instead of the ambiguous bare local name.
        assertNull(fieldOf(schema, "value"), "colliding local name must not be exposed unqualified");
        assertInstanceOf(StringSchema.class,
                fieldOf(schema, qualifiedKey("https://types-a.example.com", "value")));
        assertInstanceOf(IntegerSchema.class,
                fieldOf(schema, qualifiedKey("https://types-b.example.com", "value")));
    }

    @Test
    void namedChoiceAlternativeCollidingWithRefAlternativeIsQualified() {
        // A named element in the local schema and a ref element from another namespace share the
        // local name "value". Both must receive namespace-qualified keys; neither may be bare.
        var converter = converterForSchemas(Map.of(
                NS, """
                        <xsd:import namespace="https://other.example.com"/>
                        <xsd:element name="request">
                          <xsd:complexType><xsd:choice>
                            <xsd:element name="value" type="xsd:string"/>
                            <xsd:element ref="other:value" xmlns:other="https://other.example.com"/>
                          </xsd:choice></xsd:complexType>
                        </xsd:element>
                        """,
                "https://other.example.com", """
                        <xsd:element name="value" type="xsd:int"/>
                        """
        ));

        var schema = convert(converter, "request");

        assertNull(fieldOf(schema, "value"), "colliding local name must not appear unqualified");
        assertInstanceOf(StringSchema.class,
                fieldOf(schema, qualifiedKey(NS, "value")), "local named alternative must be NS-qualified");
        assertInstanceOf(IntegerSchema.class,
                fieldOf(schema, qualifiedKey("https://other.example.com", "value")), "ref alternative must be NS-qualified");
    }

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

        var payload = fieldOf(converter, convert(converter, "request"), "payload");

        assertInstanceOf(ObjectSchema.class, payload);
        assertInstanceOf(StringSchema.class, fieldOf(payload, "body"));
    }

    // ── components/schemas ────────────────────────────────────────────────

    @Test
    void aNamedTypeIsPublishedOnceAndReferencedFromEveryUseSite() {
        var converter = converterFor("""
                <xsd:complexType name="Address">
                  <xsd:sequence><xsd:element name="street" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>

                <xsd:element name="order">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="billing"  type="tns:Address"/>
                    <xsd:element name="shipping" type="tns:Address"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        var schema = convert(converter, "order");

        assertRefTo("Address", fieldOf(schema, "billing"));
        assertRefTo("Address", fieldOf(schema, "shipping"));
        assertEquals(Set.of("Address"), converter.getComponents().keySet());
        assertInstanceOf(StringSchema.class, fieldOf(converter.getComponents().get("Address"), "street"));
    }

    @Test
    void aTypeNoMessageRefersToIsNotPublished() {
        var converter = converterFor("""
                <xsd:complexType name="Used">
                  <xsd:sequence><xsd:element name="a" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>
                <xsd:complexType name="Unused">
                  <xsd:sequence><xsd:element name="b" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>

                <xsd:element name="request">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="used" type="tns:Used"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        convert(converter, "request");

        assertEquals(Set.of("Used"), converter.getComponents().keySet());
    }

    @Test
    void aMessageSchemaIsBuiltInlineEvenWhereItsElementNamesAType() {
        var converter = converterFor("""
                <xsd:complexType name="GetCityRequest">
                  <xsd:sequence>
                    <xsd:element name="name" type="xsd:string"/>
                    <xsd:element name="filter" type="tns:Filter"/>
                  </xsd:sequence>
                </xsd:complexType>
                <xsd:complexType name="Filter">
                  <xsd:sequence><xsd:element name="minPopulation" type="xsd:int"/></xsd:sequence>
                </xsd:complexType>

                <xsd:element name="getCity" type="tns:GetCityRequest"/>
                """);
        var schema = convert(converter, "getCity");

        // A body that is nothing but a reference says nothing about the message, and the converter
        // reads its fields to derive path and query parameters.
        assertNull(schema.get$ref());
        assertInstanceOf(StringSchema.class, fieldOf(schema, "name"));
        // The types nested inside it are still shared.
        assertRefTo("Filter", fieldOf(schema, "filter"));
    }

    @Test
    void twoNamespacesDeclaringOneLocalNameKeepTheirOwnComponent() {
        var schemas = new LinkedHashMap<String, String>();
        schemas.put(NS, """
                <xsd:element name="order">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="billTo"  type="b:Address" xmlns:b="https://billing.example.com"/>
                    <xsd:element name="shipTo"  type="s:Address" xmlns:s="https://shipping.example.com"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        schemas.put("https://billing.example.com", """
                <xsd:complexType name="Address">
                  <xsd:sequence><xsd:element name="invoiceStreet" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>
                """);
        schemas.put("https://shipping.example.com", """
                <xsd:complexType name="Address">
                  <xsd:sequence><xsd:element name="deliveryStreet" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>
                """);
        var converter = converterForSchemas(schemas);

        var schema = convert(converter, "order");

        // Sharing one component would publish two different types as the same one.
        assertRefTo("Address", fieldOf(schema, "billTo"));
        assertRefTo("shipping.example.com_Address", fieldOf(schema, "shipTo"));
        assertNotNull(fieldOf(converter, schema, "billTo").getProperties().get("invoiceStreet"));
        assertNotNull(fieldOf(converter, schema, "shipTo").getProperties().get("deliveryStreet"));
    }

    @Test
    void aNillableFieldOfANamedTypeIsInlinedAndLeavesTheComponentAlone() {
        var converter = converterFor("""
                <xsd:simpleType name="Status">
                  <xsd:restriction base="xsd:string"><xsd:enumeration value="ACTIVE"/></xsd:restriction>
                </xsd:simpleType>

                <xsd:element name="account">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="status"     type="tns:Status" nillable="true"/>
                    <xsd:element name="prevStatus" type="tns:Status"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        var schema = convert(converter, "account");

        // Nillability belongs to this one declaration, so it cannot be written onto the shared type.
        Schema<?> status = fieldOf(schema, "status");
        assertNull(status.get$ref());
        assertEquals(List.of("string", "null"), status.getTypes().stream().toList());
        assertRefTo("Status", fieldOf(schema, "prevStatus"));
        assertFalse(converter.getComponents().get("Status").getTypes().contains("null"),
                "the shared type must not have become nullable for everyone");
    }

    @Test
    void aFieldWithAFixedValueOfANamedTypeIsInlined() {
        var converter = converterFor("""
                <xsd:simpleType name="Currency">
                  <xsd:restriction base="xsd:string"/>
                </xsd:simpleType>

                <xsd:element name="payment">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="currency" type="tns:Currency" fixed="EUR"/>
                  </xsd:sequence>
                    <xsd:attribute name="unit" type="tns:Currency" fixed="EUR"/>
                  </xsd:complexType>
                </xsd:element>
                """);
        var schema = convert(converter, "payment");

        assertEquals("EUR", fieldOf(schema, "currency").getDefault());
        assertEquals("EUR", fieldOf(schema, "@unit").getDefault(),
                "an attribute carries a fixed value just as an element does");
        assertNull(converter.getComponents().get("Currency"),
                "no use site was left to publish the type for");
    }

    @Test
    void anExtensionOfANamedBaseTypeStillInheritsItsFields() {
        var converter = converterFor("""
                <xsd:complexType name="OrderBase">
                  <xsd:sequence><xsd:element name="orderId" type="xsd:string"/></xsd:sequence>
                </xsd:complexType>
                <xsd:complexType name="PriorityOrder">
                  <xsd:complexContent>
                    <xsd:extension base="tns:OrderBase">
                      <xsd:sequence><xsd:element name="priority" type="xsd:int"/></xsd:sequence>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="request">
                  <xsd:complexType><xsd:sequence>
                    <xsd:element name="order" type="tns:PriorityOrder"/>
                  </xsd:sequence></xsd:complexType>
                </xsd:element>
                """);
        var order = fieldOf(converter, convert(converter, "request"), "order");

        // A base type is flattened into the derived type: a reference would carry no fields at all.
        assertInstanceOf(StringSchema.class, fieldOf(order, "orderId"));
        assertInstanceOf(IntegerSchema.class, fieldOf(order, "priority"));
    }

    @Test
    void aTypeThatContainsTheTypeItExtendsStillInheritsItsFields() {
        var converter = converterFor("""
                <xsd:complexType name="Node">
                  <xsd:sequence><xsd:element name="child" type="tns:SubNode" minOccurs="0"/></xsd:sequence>
                </xsd:complexType>
                <xsd:complexType name="SubNode">
                  <xsd:complexContent>
                    <xsd:extension base="tns:Node">
                      <xsd:sequence><xsd:element name="label" type="xsd:string"/></xsd:sequence>
                    </xsd:extension>
                  </xsd:complexContent>
                </xsd:complexType>

                <xsd:element name="root" type="tns:Node"/>
                """);
        var subNode = fieldOf(converter, convert(converter, "root"), "child");

        // SubNode reads Node's fields while Node is itself being built, so a half-built Node in the
        // registry would cost the inherited field.
        assertInstanceOf(StringSchema.class, fieldOf(subNode, "label"));
        assertNotNull(fieldOf(subNode, "child"), "the field inherited from the base type");
    }
}
