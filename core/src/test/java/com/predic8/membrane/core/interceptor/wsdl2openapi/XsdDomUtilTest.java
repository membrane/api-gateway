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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.wsdl2openapi.XsdDomUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class XsdDomUtilTest {

    private static final String BILLING = "http://example.com/billing";
    private static final String SHIPPING = "http://example.com/shipping";

    /** Wraps each namespace's declarations in an xsd:schema and returns the map XsdToSchema uses. */
    private static Map<String, List<Element>> schemas(Map<String, String> declarationsByNamespace) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var map = new LinkedHashMap<String, List<Element>>();
            for (var entry : declarationsByNamespace.entrySet()) {
                var xml = """
                        <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="%s">
                          %s
                        </xsd:schema>
                        """.formatted(entry.getKey(), entry.getValue());
                var doc = builder.parse(new InputSource(new StringReader(xml)));
                map.put(entry.getKey(), List.of(doc.getDocumentElement()));
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Two namespaces with their declarations, in exactly the given order. */
    private static Map<String, String> inOrder(String firstNamespace, String firstDeclarations,
                                               String secondNamespace, String secondDeclarations) {
        var map = new LinkedHashMap<String, String>();
        map.put(firstNamespace, firstDeclarations);
        map.put(secondNamespace, secondDeclarations);
        return map;
    }

    private static Map<String, String> componentNames(Map<String, String> declarationsByNamespace) {
        return buildComponentNames(schemas(declarationsByNamespace), Set.of());
    }

    @Test
    void aUniqueLocalNameIsUsedAsIs() {
        var names = componentNames(Map.of(BILLING, """
                <xsd:complexType name="Address"><xsd:sequence/></xsd:complexType>
                <xsd:simpleType name="Currency"><xsd:restriction base="xsd:string"/></xsd:simpleType>
                """));

        assertEquals(Map.of(qualifiedKey(BILLING, "Address"), "Address",
                        qualifiedKey(BILLING, "Currency"), "Currency"),
                names);
    }

    @Test
    void anonymousAndNonTypeDeclarationsAreNotNamed() {
        var names = componentNames(Map.of(BILLING, """
                <xsd:element name="invoice"><xsd:complexType><xsd:sequence/></xsd:complexType></xsd:element>
                <xsd:group name="AddressBits"><xsd:sequence/></xsd:group>
                """));

        // Only named complexType/simpleType declarations become components: the inline type of the
        // element has no name to publish, and a group is a content-model fragment, not a type.
        assertEquals(Map.of(), names);
    }

    @Test
    void aLocalNameSharedByTwoNamespacesIsQualifiedForAllButTheFirst() {
        var names = componentNames(Map.of(
                SHIPPING, "<xsd:complexType name=\"Address\"><xsd:sequence/></xsd:complexType>",
                BILLING, "<xsd:complexType name=\"Address\"><xsd:sequence/></xsd:complexType>"));

        // The namespaces are sorted, so it is the lexicographically first that keeps the bare name —
        // regardless of the order the schemas were discovered in.
        assertEquals("Address", names.get(qualifiedKey(BILLING, "Address")));
        assertEquals("shipping_Address", names.get(qualifiedKey(SHIPPING, "Address")));
    }

    @Test
    void namesDoNotDependOnTheOrderTheSchemasWereDiscoveredIn() {
        String address = "<xsd:complexType name=\"Address\"><xsd:sequence/></xsd:complexType>";

        assertEquals(componentNames(inOrder(BILLING, address, SHIPPING, address)),
                componentNames(inOrder(SHIPPING, address, BILLING, address)));
    }

    @Test
    void aReservedNameIsNotHandedOut() {
        var names = componentNames(Map.of(BILLING,
                "<xsd:complexType name=\"ProblemDetails\"><xsd:sequence/></xsd:complexType>"));
        var withReservation = buildComponentNames(
                schemas(Map.of(BILLING, "<xsd:complexType name=\"ProblemDetails\"><xsd:sequence/></xsd:complexType>")),
                Set.of("ProblemDetails"));

        assertEquals("ProblemDetails", names.get(qualifiedKey(BILLING, "ProblemDetails")));
        // A WSDL may name a type exactly like a component the converter contributes itself; the
        // reservation is what keeps the two from becoming one.
        assertEquals("ProblemDetails_2", withReservation.get(qualifiedKey(BILLING, "ProblemDetails")));
    }

    @Test
    void twoNamespacesYieldingTheSamePrefixStillGetDistinctNames() {
        String address = "<xsd:complexType name=\"Address\"><xsd:sequence/></xsd:complexType>";
        var names = componentNames(Map.of(
                "urn:aaa:core", address,      // sorts first, so it keeps the bare name
                "urn:one:billing", address,
                "urn:two:billing", address)); // same prefix as the previous one

        // Every type must end up under its own key: sharing one would publish two different types
        // as the same component.
        assertEquals(3, Set.copyOf(names.values()).size(), names.toString());
        assertTrue(names.containsValue("billing_Address"));
        assertTrue(names.containsValue("billing_Address_2"));
    }

    @Test
    void everyGeneratedNameIsSafeInAReference() {
        var names = componentNames(Map.of(BILLING, """
                <xsd:complexType name="Adreße"><xsd:sequence/></xsd:complexType>
                <xsd:simpleType name="Betrag.v2"><xsd:restriction base="xsd:string"/></xsd:simpleType>
                """));

        assertFalse(names.isEmpty());
        names.values().forEach(name ->
                assertTrue(name.matches("[a-zA-Z0-9._-]+"), "not usable in a $ref path: " + name));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void sanitizeComponentNameConv(String input, String expected) {
        assertEquals(expected, sanitizeComponentName(input));
    }

    static Stream<Arguments> sanitizeComponentNameConv() {
        return Stream.of(
                arguments("Address", "Address"),
                arguments("Money.Amount-1_x", "Money.Amount-1_x"),   // all already legal
                arguments("Adreße", "Adre_e"),
                arguments("a b", "a_b"),
                arguments("a//b", "a_b"),                             // runs collapse
                arguments("_leading_", "leading"),
                arguments("ß", "type")                                // nothing legal left
        );
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource
    void nsPrefixConv(String namespace, String expected) {
        assertEquals(expected, nsPrefix(namespace));
    }

    static Stream<Arguments> nsPrefixConv() {
        return Stream.of(
                arguments("http://example.com/billing", "billing"),
                arguments("https://example.com/a/b/shipping/", "shipping"),
                arguments("urn:example:billing:v1", "billing"),       // a version segment is skipped
                arguments("http://example.com/billing/v2", "billing"),
                arguments("http://example.com/billing?x=1#y", "billing"),
                arguments("http://example.com", "example.com"),       // no path segment to take
                arguments("urn:v1", "v1")                             // nothing but a version
        );
    }
}
