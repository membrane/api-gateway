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
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.*;

import javax.xml.namespace.QName;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XsdToSchemaTest {

    static final String NS_CITIES    = "https://predic8.de/cities";
    static final String NS_EXTENDED  = "https://example.com/extended";
    static final String NS_SERVICE   = "https://example.com/service";
    static final String NS_RECURSIVE = "https://example.com/recursive";

    static XsdToSchema cities;
    static XsdToSchema extended;
    static XsdToSchema crossNs;
    static XsdToSchema recursive;

    @BeforeAll
    static void setup() throws Exception {
        var resolver = new ResolverMap();
        cities    = new XsdToSchema(Definitions.parse(resolver, "classpath:/ws/cities.wsdl"));
        extended  = new XsdToSchema(Definitions.parse(resolver, "classpath:/ws/extended-types.wsdl"));
        crossNs   = new XsdToSchema(Definitions.parse(resolver, "classpath:/ws/cross-namespace.wsdl"));
        recursive = new XsdToSchema(Definitions.parse(resolver, "classpath:/ws/recursive-type.wsdl"));
    }

    private static Schema<?> convert(XsdToSchema converter, String namespace, String element) {
        return converter.convert(new QName(namespace, element));
    }

    private static Schema<?> fieldOf(Schema<?> schema, String fieldName) {
        return schema.getProperties().get(fieldName);
    }

    private static boolean isRequired(Schema<?> schema, String fieldName) {
        var req = schema.getRequired();
        return req != null && req.contains(fieldName);
    }

    private static Schema<?> extendedTypeItems() {
        return fieldOf(convert(extended, NS_EXTENDED, "searchResponse"), "item").getItems();
    }

    @Nested
    class UnresolvableInput {

        @Test
        void unknownNamespaceProducesEmptyObjectSchema() {
            var schema = convert(cities, "https://does-not-exist.example.com", "foo");

            assertInstanceOf(ObjectSchema.class, schema);
            assertNull(schema.getProperties());
        }

        @Test
        void unknownElementInKnownNamespaceProducesEmptyObjectSchema() {
            var schema = convert(cities, NS_CITIES, "doesNotExist");

            assertInstanceOf(ObjectSchema.class, schema);
            assertNull(schema.getProperties());
        }
    }

    @Nested
    class PrimitiveTypes {
        // xsd:string  →  cities / getCityResponse.country
        // xsd:integer →  cities / getCityResponse.population
        // xsd:int     →  extended / searchResponse → item[] → baseId  (via ExtendedType extends BaseType)

        @Test
        void xsdStringMapsToStringSchema() {
            var getCityResponse = convert(cities, NS_CITIES, "getCityResponse");
            assertInstanceOf(StringSchema.class, fieldOf(getCityResponse, "country"));
        }

        @Test
        void xsdIntegerMapsToIntegerSchema() {
            var getCityResponse = convert(cities, NS_CITIES, "getCityResponse");
            assertInstanceOf(IntegerSchema.class, fieldOf(getCityResponse, "population"));
        }

        @Test
        void xsdIntMapsToIntegerSchema() {
            assertInstanceOf(IntegerSchema.class, fieldOf(extendedTypeItems(), "baseId"));
        }
    }

    @Nested
    class SequenceFields {
        // cities / getCity:         { name: xsd:string }
        // cities / getCityResponse: { country: xsd:string, population: xsd:integer }
        // extended / searchRequest: { <choice>, code: CodeType (minOccurs=0) }

        @Test
        void inlineComplexTypeWithSequenceProducesObjectSchema() {
            var getCity = convert(cities, NS_CITIES, "getCity");
            assertInstanceOf(ObjectSchema.class, getCity);
        }

        @Test
        void sequenceFieldTypesAreResolvedCorrectly() {
            var getCityResponse = convert(cities, NS_CITIES, "getCityResponse");
            assertInstanceOf(StringSchema.class,  fieldOf(getCityResponse, "country"));
            assertInstanceOf(IntegerSchema.class, fieldOf(getCityResponse, "population"));
        }

        @Test
        void fieldWithoutMinOccursIsRequired() {
            var getCity = convert(cities, NS_CITIES, "getCity");
            assertTrue(isRequired(getCity, "name"));
        }

        @Test
        void allFieldsWithoutMinOccursAreRequired() {
            var getCityResponse = convert(cities, NS_CITIES, "getCityResponse");
            assertTrue(isRequired(getCityResponse, "country"));
            assertTrue(isRequired(getCityResponse, "population"));
        }

        @Test
        void fieldWithMinOccursZeroIsNotRequired() {
            var searchRequest = convert(extended, NS_EXTENDED, "searchRequest");
            assertNotNull(fieldOf(searchRequest, "code"));
            assertFalse(isRequired(searchRequest, "code"));
        }
    }

    @Nested
    class ChoiceFields {
        // extended / searchRequest:
        //   <xsd:sequence>
        //     <xsd:choice>
        //       <xsd:element name="byName" type="xsd:string"/>
        //       <xsd:element name="byId"   type="xsd:int"/>
        //     </xsd:choice>
        //     <xsd:element name="code" type="tns:CodeType" minOccurs="0"/>
        //   </xsd:sequence>

        @Test
        void allAlternativesAreExposedAsProperties() {
            var searchRequest = convert(extended, NS_EXTENDED, "searchRequest");
            assertNotNull(fieldOf(searchRequest, "byName"));
            assertNotNull(fieldOf(searchRequest, "byId"));
        }

        @Test
        void noChoiceAlternativeIsRequired() {
            var searchRequest = convert(extended, NS_EXTENDED, "searchRequest");
            assertFalse(isRequired(searchRequest, "byName"));
            assertFalse(isRequired(searchRequest, "byId"));
        }

        @Test
        void alternativesMapToTheirPrimitiveTypes() {
            var searchRequest = convert(extended, NS_EXTENDED, "searchRequest");
            assertInstanceOf(StringSchema.class,  fieldOf(searchRequest, "byName"));
            assertInstanceOf(IntegerSchema.class, fieldOf(searchRequest, "byId"));
        }
    }

    @Nested
    class UnboundedFields {
        // extended / searchResponse:
        //   <xsd:element name="item" type="tns:ExtendedType" minOccurs="0" maxOccurs="unbounded"/>

        @Test
        void maxOccursUnboundedProducesArraySchema() {
            var searchResponse = convert(extended, NS_EXTENDED, "searchResponse");
            assertInstanceOf(ArraySchema.class, fieldOf(searchResponse, "item"));
        }

        @Test
        void arrayItemsReflectTheReferencedComplexType() {
            var searchResponse = convert(extended, NS_EXTENDED, "searchResponse");
            var itemArray = (ArraySchema) fieldOf(searchResponse, "item");
            assertInstanceOf(ObjectSchema.class, itemArray.getItems());
        }

        @Test
        void unboundedFieldWithMinOccursZeroIsNotRequired() {
            var searchResponse = convert(extended, NS_EXTENDED, "searchResponse");
            assertFalse(isRequired(searchResponse, "item"));
        }
    }

    @Nested
    class ExtensionInheritance {
        // extended-types.wsdl:
        //   BaseType     { baseId: xsd:int, baseName: xsd:string }
        //   ExtendedType extends BaseType, adds { extra: xsd:string }
        // Reached via: searchResponse → item (ArraySchema) → items (ExtendedType schema)

        @Test
        void baseTypeFieldsArePresentInExtendedSchema() {
            var extendedType = extendedTypeItems();
            assertNotNull(fieldOf(extendedType, "baseId"));
            assertNotNull(fieldOf(extendedType, "baseName"));
        }

        @Test
        void ownFieldFromExtendingTypeIsAlsoPresent() {
            assertNotNull(fieldOf(extendedTypeItems(), "extra"));
        }

        @Test
        void allInheritedAndOwnFieldsHaveCorrectTypes() {
            var extendedType = extendedTypeItems();
            assertInstanceOf(IntegerSchema.class, fieldOf(extendedType, "baseId"));
            assertInstanceOf(StringSchema.class,  fieldOf(extendedType, "baseName"));
            assertInstanceOf(StringSchema.class,  fieldOf(extendedType, "extra"));
        }
    }

    @Nested
    class SimpleTypeRestriction {
        // extended-types.wsdl:
        //   <xsd:simpleType name="CodeType">
        //     <xsd:restriction base="xsd:string"/>
        //   </xsd:simpleType>
        // searchRequest.code has type tns:CodeType

        @Test
        void restrictionOfXsdStringResolvesToStringSchema() {
            var searchRequest = convert(extended, NS_EXTENDED, "searchRequest");
            assertInstanceOf(StringSchema.class, fieldOf(searchRequest, "code"));
        }
    }

    @Nested
    class CrossNamespaceResolution {
        // cross-namespace.wsdl — two embedded schemas:
        //   NS=service  getItemRequest.item  →  type="types:ItemType"
        //   NS=types    ItemType             →  { itemName: xsd:string, itemCount: xsd:int }

        @Test
        void typeFromDifferentNamespaceIsResolved() {
            var getItemRequest = convert(crossNs, NS_SERVICE, "getItemRequest");
            var item = fieldOf(getItemRequest, "item");
            assertInstanceOf(ObjectSchema.class, item);
            assertNotNull(fieldOf(item, "itemName"));
            assertNotNull(fieldOf(item, "itemCount"));
        }

        @Test
        void resolvedCrossNamespaceFieldsHaveCorrectTypes() {
            var item = fieldOf(convert(crossNs, NS_SERVICE, "getItemRequest"), "item");
            assertInstanceOf(StringSchema.class,  fieldOf(item, "itemName"));
            assertInstanceOf(IntegerSchema.class, fieldOf(item, "itemCount"));
        }
    }

    @Nested
    class SelfReferencingTypes {
        // recursive-type.wsdl:
        //   TreeNode { value: xsd:string, children: TreeNode (minOccurs=0, maxOccurs=unbounded) }
        // processTreeRequest.root has type tns:TreeNode

        @Test
        void conversionCompletesWithoutStackOverflow() {
            assertDoesNotThrow(() -> convert(recursive, NS_RECURSIVE, "processTreeRequest"));
        }

        @Test
        void nonRecursiveFieldIsPreservedAfterCycleDetection() {
            var processTreeRequest = convert(recursive, NS_RECURSIVE, "processTreeRequest");
            var root = fieldOf(processTreeRequest, "root");
            assertNotNull(fieldOf(root, "value"));
        }

        @Test
        void recursiveFieldBecomesArrayOfEmptyObjectSchema() {
            var root = fieldOf(convert(recursive, NS_RECURSIVE, "processTreeRequest"), "root");
            var children = (ArraySchema) fieldOf(root, "children");
            assertInstanceOf(ObjectSchema.class, children.getItems());
            assertNull(children.getItems().getProperties());
        }
    }

    @Nested
    class MessageParts {

        @Test
        void emptyMessageListProducesEmptyObjectSchema() {
            var schema = cities.convertMessageParts(List.of());
            assertInstanceOf(ObjectSchema.class, schema);
            assertNull(schema.getProperties());
        }
    }
}
