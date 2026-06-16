/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.JsonNode;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"rawtypes", "unchecked"})
class XmlToJsonConverterTest {

    private XmlToJsonConverter converter;

    @BeforeEach
    void setUp() {
        converter = new XmlToJsonConverter(new OpenAPI());
    }

    @Nested
    class Primitives {

        @Test
        void typedPrimitivesAreConverted() throws Exception {
            String xml = """
                    <order>
                      <id>4711</id>
                      <customer>Anna Müller</customer>
                      <active>true</active>
                      <total>19.99</total>
                    </order>
                    """;
            Schema schema = obj()
                    .addProperty("id", integer())
                    .addProperty("customer", str())
                    .addProperty("active", bool())
                    .addProperty("total", number());

            JsonNode node = converter.convert(xml, schema);

            assertTrue(node.get("id").isIntegralNumber());
            assertEquals(4711, node.get("id").asInt());
            assertEquals("Anna Müller", node.get("customer").asText());
            assertTrue(node.get("active").isBoolean());
            assertTrue(node.get("active").asBoolean());
            assertEquals(19.99, node.get("total").asDouble(), 1e-9);
        }

        @Test
        void surroundingWhitespaceIsTrimmed() throws Exception {
            String xml = """
                    <order>
                      <id>   4711   </id>
                      <customer>
                          Anna
                      </customer>
                    </order>
                    """;
            JsonNode node = converter.convert(xml, obj()
                    .addProperty("id", integer())
                    .addProperty("customer", str()));

            assertEquals(4711, node.get("id").asInt());
            assertEquals("Anna", node.get("customer").asText());
        }

        @Test
        void objectWithoutExplicitTypeButPropertiesIsTreatedAsObject() throws Exception {
            // No type set; the converter falls back to "object" because properties exist.
            JsonNode node = converter.convert("<order><id>5</id></order>",
                    new Schema().addProperty("id", integer()));

            assertTrue(node.isObject());
            assertEquals(5, node.get("id").asInt());
        }

        @Test
        void nestedObjectsAreConverted() throws Exception {
            String xml = """
                    <order>
                      <id>1</id>
                      <address>
                        <city>Berlin</city>
                        <zip>10115</zip>
                      </address>
                    </order>
                    """;
            Schema schema = obj()
                    .addProperty("id", integer())
                    .addProperty("address", obj()
                            .addProperty("city", str())
                            .addProperty("zip", integer()));

            JsonNode node = converter.convert(xml, schema);

            assertEquals("Berlin", node.get("address").get("city").asText());
            assertEquals(10115, node.get("address").get("zip").asInt());
        }

        @Test
        void elementNameCanBeOverriddenWithXmlName() throws Exception {
            // JSON property "fullName" is taken from the differently-named XML element <name>.
            String xml = """
                    <person>
                      <name>Jane Doe</name>
                    </person>
                    """;
            Schema schema = obj()
                    .addProperty("fullName", str().xml(new XML().name("name")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals("Jane Doe", node.get("fullName").asText());
            assertFalse(node.has("name"));
        }

        @Test
        void elementNameOverrideAppliesToNestedObjects() throws Exception {
            // The renamed element also carries an object whose own properties are renamed.
            String xml = """
                    <person>
                      <addr>
                        <town>Berlin</town>
                      </addr>
                    </person>
                    """;
            Schema schema = obj()
                    .addProperty("address", obj()
                            .addProperty("city", str().xml(new XML().name("town")))
                            .xml(new XML().name("addr")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals("Berlin", node.get("address").get("city").asText());
        }
    }

    // -----------------------------------------------------------------------
    // Booleans (xs:boolean lexical space: true, false, 1, 0)
    // -----------------------------------------------------------------------

    @Nested
    class Booleans {

        @Test
        void allXmlSchemaBooleanLiteralsAreAccepted() throws Exception {
            assertTrue(boolValueOf("true"));
            assertFalse(boolValueOf("false"));
            assertTrue(boolValueOf("1"));
            assertFalse(boolValueOf("0"));
        }

        @Test
        void numericBooleanBecomesARealBooleanNode() throws Exception {
            JsonNode node = converter.convert("<order><active>1</active></order>",
                    obj().addProperty("active", bool()));

            assertTrue(node.get("active").isBoolean());
            assertTrue(node.get("active").asBoolean());
        }

        @Test
        void numbersOtherThanZeroAndOneAreNotBooleans() throws Exception {
            // Only 0 and 1 are valid xs:boolean literals; 2 stays text so the validator can reject it.
            JsonNode node = converter.convert("<order><active>2</active></order>",
                    obj().addProperty("active", bool()));

            assertFalse(node.get("active").isBoolean());
            assertEquals("2", node.get("active").asText());
        }

        @Test
        void booleanLiteralsAreCaseSensitivePerXsd() throws Exception {
            // xs:boolean is case-sensitive: "True" is not a valid literal and stays text.
            JsonNode node = converter.convert("<order><active>True</active></order>",
                    obj().addProperty("active", bool()));

            assertFalse(node.get("active").isBoolean());
            assertEquals("True", node.get("active").asText());
        }

        private boolean boolValueOf(String literal) throws Exception {
            JsonNode node = converter.convert("<order><active>" + literal + "</active></order>",
                    obj().addProperty("active", bool()));
            assertTrue(node.get("active").isBoolean(), () -> "Expected a boolean for literal '" + literal + "'");
            return node.get("active").asBoolean();
        }
    }

    // -----------------------------------------------------------------------
    // Attributes (xml.attribute = true)
    // -----------------------------------------------------------------------

    @Nested
    class Attributes {

        @Test
        void attributesAreMappedToProperties() throws Exception {
            String xml = """
                    <product sku="A-1" quantity="5">
                      <name>Book</name>
                    </product>
                    """;
            Schema schema = obj()
                    .addProperty("sku", str().xml(new XML().attribute(true)))
                    .addProperty("quantity", integer().xml(new XML().attribute(true)))
                    .addProperty("name", str());

            JsonNode node = converter.convert(xml, schema);

            assertEquals("A-1", node.get("sku").asText());
            assertTrue(node.get("quantity").isIntegralNumber());
            assertEquals(5, node.get("quantity").asInt());
            assertEquals("Book", node.get("name").asText());
        }

        @Test
        void attributeNameCanBeOverriddenWithXmlName() throws Exception {
            // Property "id" is taken from the XML attribute "productId".
            Schema schema = obj()
                    .addProperty("id", integer().xml(new XML().attribute(true).name("productId")));

            JsonNode node = converter.convert("<product productId=\"99\"/>", schema);

            assertEquals(99, node.get("id").asInt());
        }

        @Test
        void missingAttributeIsOmitted() throws Exception {
            Schema schema = obj()
                    .addProperty("id", integer().xml(new XML().attribute(true)));

            JsonNode node = converter.convert("<product/>", schema);

            assertFalse(node.has("id"));
        }
    }

    // -----------------------------------------------------------------------
    // Namespaces
    // -----------------------------------------------------------------------

    @Nested
    class Namespaces {

        @Test
        void defaultNamespaceIsTransparent() throws Exception {
            // Elements in a default namespace have no prefix, so they match by their local name.
            String xml = """
                    <order xmlns="http://example.com/ns">
                      <id>1</id>
                      <customer>Bob</customer>
                    </order>
                    """;
            JsonNode node = converter.convert(xml, obj()
                    .addProperty("id", integer())
                    .addProperty("customer", str()));

            assertEquals(1, node.get("id").asInt());
            assertEquals("Bob", node.get("customer").asText());
        }

        @Test
        void prefixedElementsMatchWhenXmlNameContainsThePrefix() throws Exception {
            String xml = """
                    <ns:order xmlns:ns="http://example.com/ns">
                      <ns:id>1</ns:id>
                      <ns:customer>Bob</ns:customer>
                    </ns:order>
                    """;
            Schema schema = obj()
                    .addProperty("id", integer().xml(new XML().name("ns:id")))
                    .addProperty("customer", str().xml(new XML().name("ns:customer")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals(1, node.get("id").asInt());
            assertEquals("Bob", node.get("customer").asText());
        }

        @Test
        void prefixedElementsAreNotMatchedWithoutThePrefixInXmlName() throws Exception {
            // Documents the current (prefix-sensitive) behaviour: the qualified tag name "ns:id"
            // does not match the plain property name "id".
            String xml = """
                    <ns:order xmlns:ns="http://example.com/ns">
                      <ns:id>1</ns:id>
                    </ns:order>
                    """;
            JsonNode node = converter.convert(xml, obj().addProperty("id", integer()));

            assertFalse(node.has("id"));
        }

        @Test
        void namespacedAttributeIsMappedViaQualifiedXmlName() throws Exception {
            String xml = """
                    <product xmlns:ns="http://example.com/ns" ns:code="X1"/>
                    """;
            Schema schema = obj()
                    .addProperty("code", str().xml(new XML().attribute(true).name("ns:code")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals("X1", node.get("code").asText());
        }

        @Test
        void prefixSetInOpenAPIIsCombinedWithLocalName() throws Exception {
            // Idiomatic OpenAPI: the prefix is declared via xml.prefix, the local name via xml.name.
            String xml = """
                    <ns:order xmlns:ns="http://example.com/ns">
                      <ns:id>1</ns:id>
                      <ns:customer>Bob</ns:customer>
                    </ns:order>
                    """;
            Schema schema = obj()
                    .addProperty("id", integer().xml(new XML().prefix("ns").name("id")))
                    .addProperty("customer", str().xml(new XML().prefix("ns").name("customer")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals(1, node.get("id").asInt());
            assertEquals("Bob", node.get("customer").asText());
        }

        @Test
        void prefixSetInOpenAPIWithoutExplicitNameUsesPropertyName() throws Exception {
            // Only xml.prefix is set; the local name defaults to the property name.
            String xml = """
                    <ns:order xmlns:ns="http://example.com/ns">
                      <ns:id>1</ns:id>
                    </ns:order>
                    """;
            Schema schema = obj()
                    .addProperty("id", integer().xml(new XML().prefix("ns")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals(1, node.get("id").asInt());
        }

        @Test
        void prefixSetInOpenAPIAppliesToAttributes() throws Exception {
            String xml = """
                    <product xmlns:ns="http://example.com/ns" ns:code="X1"/>
                    """;
            Schema schema = obj()
                    .addProperty("code", str().xml(new XML().attribute(true).prefix("ns").name("code")));

            JsonNode node = converter.convert(xml, schema);

            assertEquals("X1", node.get("code").asText());
        }

        @Test
        void prefixSetInOpenAPIAppliesToWrappedArrays() throws Exception {
            // Both the wrapper element and its items are namespaced via xml.prefix.
            String xml = """
                    <ns:order xmlns:ns="http://example.com/ns">
                      <ns:items>
                        <ns:item><id>10</id></ns:item>
                        <ns:item><id>11</id></ns:item>
                      </ns:items>
                    </ns:order>
                    """;
            Schema item = obj().addProperty("id", integer());
            Schema schema = obj()
                    .addProperty("items", array(item).xml(new XML().prefix("ns").wrapped(true)));

            JsonNode node = converter.convert(xml, schema);

            JsonNode items = node.get("items");
            assertTrue(items.isArray());
            assertEquals(2, items.size());
            assertEquals(10, items.get(0).get("id").asInt());
            assertEquals(11, items.get(1).get("id").asInt());
        }
    }

    @Nested
    class MixedContent {

        @Test
        void mixedContentWithInlineMarkupIsRejected() {
            // Text interleaved with inline markup cannot be mapped onto a schema -> error.
            assertThrows(MixedContentException.class,
                    () -> converter.convert("<note>Hello <b>World</b>!</note>", str()));
        }

        @Test
        void looseTextNextToChildElementsInObjectIsRejected() {
            // Rejecting this prevents silently dropping the loose text (a validation blind spot).
            String xml = """
                    <comment>
                      this loose text would be dropped
                      <author>Jane</author>
                      more dropped text
                    </comment>
                    """;
            assertThrows(MixedContentException.class,
                    () -> converter.convert(xml, obj().addProperty("author", str())));
        }

        @Test
        void textAfterAChildElementIsRejected() {
            // Position of the significant text does not matter.
            String xml = """
                    <order>
                      <id>1</id>
                      trailing text
                    </order>
                    """;
            assertThrows(MixedContentException.class,
                    () -> converter.convert(xml, obj().addProperty("id", integer())));
        }

        @Test
        void whitespaceBetweenElementsIsNotMixedContent() throws Exception {
            // Indentation / newlines between elements are insignificant and must be accepted.
            String xml = """
                    <order>
                      <id>1</id>
                      <customer>Bob</customer>
                    </order>
                    """;
            JsonNode node = converter.convert(xml, obj()
                    .addProperty("id", integer())
                    .addProperty("customer", str()));

            assertEquals(1, node.get("id").asInt());
            assertEquals("Bob", node.get("customer").asText());
        }

        @Test
        void textOnlyLeafElementIsNotMixedContent() throws Exception {
            // A scalar element whose content is pure text is fine, even with surrounding whitespace.
            JsonNode node = converter.convert("<customer>  Anna  </customer>", str());

            assertEquals("Anna", node.asText());
        }
    }

    // -----------------------------------------------------------------------
    // Repeated elements where a single value is expected
    // -----------------------------------------------------------------------

    @Nested
    class DuplicateElements {

        @Test
        void repeatedSingleValuedElementIsRejected() {
            // <id> appears twice but the schema declares a single integer -> error instead of
            // silently keeping only the first occurrence.
            String xml = """
                    <order>
                      <id>1</id>
                      <id>2</id>
                    </order>
                    """;
            assertThrows(MultipleElementsException.class,
                    () -> converter.convert(xml, obj().addProperty("id", integer())));
        }

        @Test
        void repeatedSingleValuedElementInNestedObjectIsRejected() {
            String xml = """
                    <order>
                      <address><city>A</city></address>
                      <address><city>B</city></address>
                    </order>
                    """;
            Schema schema = obj()
                    .addProperty("address", obj().addProperty("city", str()));

            assertThrows(MultipleElementsException.class,
                    () -> converter.convert(xml, schema));
        }

        @Test
        void repeatedWrapperElementForArrayIsRejected() {
            String xml = """
                    <order>
                      <items><item><id>1</id></item></items>
                      <items><item><id>2</id></item></items>
                    </order>
                    """;
            Schema item = obj().addProperty("id", integer());
            Schema schema = obj()
                    .addProperty("items", array(item).xml(new XML().wrapped(true)));

            assertThrows(MultipleElementsException.class,
                    () -> converter.convert(xml, schema));
        }

        @Test
        void repeatedElementForArrayPropertyIsAllowed() throws Exception {
            // The whole point of an array: multiple occurrences are expected, not rejected.
            String xml = """
                    <order>
                      <tag>red</tag>
                      <tag>green</tag>
                    </order>
                    """;
            Schema schema = obj()
                    .addProperty("tags", array(str().xml(new XML().name("tag"))));

            JsonNode node = converter.convert(xml, schema);

            assertTrue(node.get("tags").isArray());
            assertEquals(2, node.get("tags").size());
        }

        @Test
        void singleOccurrenceIsAccepted() throws Exception {
            JsonNode node = converter.convert("<order><id>1</id></order>",
                    obj().addProperty("id", integer()));

            assertEquals(1, node.get("id").asInt());
        }
    }

    @Nested
    class Arrays {

        @Test
        void wrappedArrayOfObjects() throws Exception {
            String xml = """
                    <order>
                      <items>
                        <item><productId>P10</productId><quantity>2</quantity></item>
                        <item><productId>P11</productId><quantity>1</quantity></item>
                      </items>
                    </order>
                    """;
            Schema item = obj()
                    .addProperty("productId", str())
                    .addProperty("quantity", integer());
            Schema schema = obj()
                    .addProperty("items", array(item).xml(new XML().wrapped(true)));

            JsonNode node = converter.convert(xml, schema);

            JsonNode items = node.get("items");
            assertTrue(items.isArray());
            assertEquals(2, items.size());
            assertEquals("P10", items.get(0).get("productId").asText());
            assertEquals(2, items.get(0).get("quantity").asInt());
            assertEquals("P11", items.get(1).get("productId").asText());
            assertEquals(1, items.get(1).get("quantity").asInt());
        }

        @Test
        void wrappedArrayWrapperNameCanBeOverriddenWithXmlName() throws Exception {
            // The JSON property "products" maps to a wrapper element renamed to <items> via xml.name.
            String xml = """
                    <order>
                      <items>
                        <product><id>10</id></product>
                        <product><id>11</id></product>
                      </items>
                    </order>
                    """;
            Schema item = obj().addProperty("id", integer());
            Schema schema = obj()
                    .addProperty("products", array(item).xml(new XML().name("items").wrapped(true)));

            JsonNode node = converter.convert(xml, schema);

            JsonNode products = node.get("products");
            assertTrue(products.isArray());
            assertEquals(2, products.size());
            assertEquals(10, products.get(0).get("id").asInt());
            assertEquals(11, products.get(1).get("id").asInt());
            assertFalse(node.has("items"));
        }

        @Test
        void unwrappedArrayOfPrimitives() throws Exception {
            String xml = """
                    <order>
                      <tag>red</tag>
                      <tag>green</tag>
                      <tag>blue</tag>
                    </order>
                    """;
            // Items are direct children named "tag"; the item schema carries that name.
            Schema schema = obj()
                    .addProperty("tags", array(str().xml(new XML().name("tag"))));

            JsonNode node = converter.convert(xml, schema);

            JsonNode tags = node.get("tags");
            assertTrue(tags.isArray());
            assertEquals(3, tags.size());
            assertEquals("red", tags.get(0).asText());
            assertEquals("green", tags.get(1).asText());
            assertEquals("blue", tags.get(2).asText());
        }

        @Test
        void wrappedArrayWithMissingWrapperYieldsEmptyArray() throws Exception {
            Schema schema = obj()
                    .addProperty("items", array(str()).xml(new XML().wrapped(true)));

            JsonNode node = converter.convert("<order/>", schema);

            assertTrue(node.get("items").isArray());
            assertEquals(0, node.get("items").size());
        }

        @Test
        void arrayAtRoot() throws Exception {
            // An array schema as the conversion root iterates over all child elements.
            String xml = """
                    <numbers>
                      <a>1</a>
                      <b>2</b>
                      <c>3</c>
                    </numbers>
                    """;
            JsonNode node = converter.convert(xml, array(integer()));

            assertTrue(node.isArray());
            assertEquals(3, node.size());
            assertEquals(1, node.get(0).asInt());
            assertEquals(3, node.get(2).asInt());
        }
    }

    // -----------------------------------------------------------------------
    // $ref resolution
    // -----------------------------------------------------------------------

    @Nested
    class References {

        @Test
        void rootRefIsResolved() throws Exception {
            Schema order = obj()
                    .addProperty("id", integer())
                    .addProperty("customer", str());
            OpenAPI api = new OpenAPI().components(new Components().addSchemas("Order", order));

            JsonNode node = new XmlToJsonConverter(api)
                    .convert("<order><id>1</id><customer>Bob</customer></order>",
                            new Schema().$ref("#/components/schemas/Order"));

            assertEquals(1, node.get("id").asInt());
            assertEquals("Bob", node.get("customer").asText());
        }

        @Test
        void nestedPropertyRefIsResolved() throws Exception {
            Schema address = obj().addProperty("city", str());
            Schema order = obj()
                    .addProperty("id", integer())
                    .addProperty("address", new Schema().$ref("#/components/schemas/Address"));
            OpenAPI api = new OpenAPI().components(new Components()
                    .addSchemas("Order", order)
                    .addSchemas("Address", address));

            JsonNode node = new XmlToJsonConverter(api)
                    .convert("<order><id>1</id><address><city>Berlin</city></address></order>",
                            new Schema().$ref("#/components/schemas/Order"));

            assertEquals("Berlin", node.get("address").get("city").asText());
        }
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Nested
    class EdgeCases {

        @Test
        void nullSchemaFallsBackToTextContent() throws Exception {
            JsonNode node = converter.convert("<value>  hello  </value>", null);

            assertTrue(node.isTextual());
            assertEquals("hello", node.asText());
        }

        @Test
        void invalidIntegerFallsBackToTextSoTheValidatorCanReportIt() throws Exception {
            JsonNode node = converter.convert("<order><id>not-a-number</id></order>",
                    obj().addProperty("id", integer()));

            assertTrue(node.get("id").isTextual());
            assertEquals("not-a-number", node.get("id").asText());
        }

        @Test
        void invalidBooleanFallsBackToText() throws Exception {
            JsonNode node = converter.convert("<order><active>yes</active></order>",
                    obj().addProperty("active", bool()));

            assertFalse(node.get("active").isBoolean());
            assertEquals("yes", node.get("active").asText());
        }

        @Test
        void emptyElementBecomesEmptyString() throws Exception {
            JsonNode node = converter.convert("<order><customer/></order>",
                    obj().addProperty("customer", str()));

            assertTrue(node.has("customer"));
            assertEquals("", node.get("customer").asText());
        }

        @Test
        void missingOptionalElementIsOmitted() throws Exception {
            JsonNode node = converter.convert("<order><id>1</id></order>",
                    obj().addProperty("id", integer()).addProperty("note", str()));

            assertTrue(node.has("id"));
            assertFalse(node.has("note"));
        }

        @Test
        void malformedXmlThrows() {
            assertThrows(XmlParseException.class,
                    () -> converter.convert("<order><unclosed>", obj()));
        }

        @Test
        void doctypeIsRejected() {
            String xml = """
                    <!DOCTYPE order [<!ENTITY x "expanded">]>
                    <order><id>1</id></order>
                    """;
            assertThrows(XmlParseException.class,
                    () -> converter.convert(xml, obj().addProperty("id", integer())));
        }
    }

    // -----------------------------------------------------------------------
    // Schema builder helpers
    // -----------------------------------------------------------------------

    private static Schema obj() {
        return new Schema().type("object");
    }

    private static Schema str() {
        return new Schema().type("string");
    }

    private static Schema integer() {
        return new Schema().type("integer");
    }

    private static Schema number() {
        return new Schema().type("number");
    }

    private static Schema bool() {
        return new Schema().type("boolean");
    }

    private static Schema array(Schema items) {
        return new Schema().type("array").items(items);
    }
}
