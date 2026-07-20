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

import static org.junit.jupiter.api.Assertions.*;

class OpenApiGeneratorTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;
    static Definitions extendedDefinitions;
    static Definitions crossNsDefinitions;
    static Definitions recursiveDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        extendedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/extended-types.wsdl");
        crossNsDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace.wsdl");
        recursiveDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/recursive-type.wsdl");
    }

    @Test
    void openApiHeader() {
        var yaml = generator(citiesDefinitions, "/purchasing");

        assertTrue(yaml.contains("openapi: \"3.0.0\""), "Should contain openapi version");
        assertTrue(yaml.contains("info:"), "Should contain info section");
        assertTrue(yaml.contains("version: \"1.0.0\""), "Should contain version");
    }

    @Test
    void serviceNameUsedAsTitle() {
        assertTrue(generator(citiesDefinitions, "/purchasing").contains("title: \"CityService\""));
    }

    @Test
    void serverUrlFromBasePath() {
        assertTrue(generator(citiesDefinitions, "/purchasing").contains("url: \"/purchasing\""));
    }

    @Test
    void basePathTrailingSlashStripped() {
        var yaml = new OpenApiGenerator(citiesDefinitions, "/purchasing/").generateYaml();

        assertTrue(yaml.contains("url: \"/purchasing\""));
        assertFalse(yaml.contains("url: \"/purchasing/\""));
    }

    @Test
    void camelCaseOperationMappedToKebabPath() {
        assertTrue(generator(citiesDefinitions, "/").contains("/get-city:"));
    }

    @Test
    void operationUsesPostMethod() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("post:"));
        assertFalse(yaml.contains("get:"));
    }

    @Test
    void operationIdMatchesOriginalCamelCase() {
        assertTrue(generator(citiesDefinitions, "/").contains("operationId: \"getCity\""));
    }

    @Test
    void requestBodyIsRequired() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("required: true"));
        assertTrue(yaml.contains("application/json:"));
    }

    @Test
    void responsesWith200And500() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("\"200\":"));
        assertTrue(yaml.contains("\"500\":"));
    }

    @Test
    void allPortTypeOperationsAreGenerated() {
        assertTrue(new OpenApiGenerator(citiesDefinitions, "/").generateYaml().contains("/get-city:"));
        assertTrue(new OpenApiGenerator(blzDefinitions, "/blz").generateYaml().contains("/get-bank:"));
    }

    @Test
    void blzServiceTitle() {
        var yaml = generator(blzDefinitions, "/blz");

        assertTrue(yaml.contains("title: \"BLZService\""));
        assertTrue(yaml.contains("/get-bank:"));
    }

    @Test
    void citiesProducesGetCityPath() {
        var yaml = new OpenApiGenerator(citiesDefinitions, "/").generateYaml();

        assertTrue(yaml.contains("paths:"));
        assertTrue(yaml.contains("/get-city:"));
    }

    // --- Inline complexType schema extraction ---

    @Test
    void getCityRequestHasNameField() {
        assertTrue(generator(citiesDefinitions, "/").contains("name:"));
    }

    @Test
    void getCityResponseHasCountryAndPopulation() {
        var yaml = generator(citiesDefinitions, "/");

        assertTrue(yaml.contains("country:"));
        assertTrue(yaml.contains("population:"));
    }

    // --- Type-reference schema extraction ---

    @Test
    void getBankRequestHasBLZField() {
        assertTrue(generator(blzDefinitions, "/blz").contains("blz:"));
    }

    @Test
    void getBankResponseHasDetailsObject() {
        var yaml = generator(blzDefinitions, "/blz");

        assertTrue(yaml.contains("details:"));
        assertTrue(yaml.contains("bezeichnung:"));
    }

    // --- xsd:extension ---

    @Test
    void extensionInheritsBaseTypeFields() {
        // searchResponse -> item: ExtendedType extends BaseType
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("baseId:"), "Should contain 'baseId' inherited from BaseType");
        assertTrue(yaml.contains("baseName:"), "Should contain 'baseName' inherited from BaseType");
        assertTrue(yaml.contains("extra:"), "Should contain 'extra' from ExtendedType");
    }

    // --- maxOccurs="unbounded" ---

    @Test
    void unboundedElementProducesArraySchema() {
        // searchResponse -> item with maxOccurs="unbounded"
        assertTrue(generator(extendedDefinitions, "/").contains("type: \"array\""));
    }

    // --- xsd:choice ---

    @Test
    void choiceAlternativesAreAllPresent() {
        // searchRequest has <xsd:choice> with byName and byId
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("byName:"), "Should contain 'byName' from choice");
        assertTrue(yaml.contains("byId:"), "Should contain 'byId' from choice");
    }

    // --- Named xsd:simpleType ---

    @Test
    void namedSimpleTypeRestrictedToStringMapsToString() {
        // CodeType is a simpleType restriction of xsd:string
        assertTrue(generator(extendedDefinitions, "/").contains("code:"));
    }

    // --- Cross-namespace type references ---

    @Test
    void crossNamespaceTypeIsResolved() {
        // getItemRequest -> item: types:ItemType (from a different embedded schema namespace)
        var yaml = generator(crossNsDefinitions, "/");

        assertTrue(yaml.contains("itemName:"), "Should resolve ItemType from the types namespace");
        assertTrue(yaml.contains("itemCount:"), "Should resolve ItemType from the types namespace");
    }

    // --- Recursive type references ---

    @Test
    void selfReferencingTypeDoesNotCauseStackOverflow() {
        assertDoesNotThrow(() -> generator(recursiveDefinitions, "/"),
                "Self-referencing complexType must not cause StackOverflowError");
    }

    @Test
    void selfReferencingTypePreservesNonRecursiveFields() {
        var yaml = generator(recursiveDefinitions, "/");
        assertTrue(yaml.contains("value:"), "Non-recursive 'value' field should be present");
    }

    // --- Helper ---

    private static String generator(Definitions defs, String basePath) {
        return new OpenApiGenerator(defs, basePath).generateYaml();
    }
}
