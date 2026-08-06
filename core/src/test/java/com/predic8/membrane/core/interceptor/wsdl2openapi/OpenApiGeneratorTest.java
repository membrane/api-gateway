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
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.wsdl.parser.Definitions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiGeneratorTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;
    static Definitions extendedDefinitions;
    static Definitions crossNsDefinitions;
    static Definitions recursiveDefinitions;
    static Definitions articleDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
        extendedDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/extended-types.wsdl");
        crossNsDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cross-namespace.wsdl");
        recursiveDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/recursive-type.wsdl");
        articleDefinitions = Definitions.parse(new ResolverMap(), "classpath:/validation/article-service.wsdl");
    }

    @Test
    void openApiHeader() {
        var yaml = generator(citiesDefinitions, "/purchasing");

        assertTrue(yaml.contains("openapi: \"3.1.0\""), "Should contain openapi version");
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
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/purchasing/").generateYaml();

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
        assertTrue(new Wsdl2OpenApiConverter(citiesDefinitions, "/").generateYaml().contains("/get-city:"));
        assertTrue(new Wsdl2OpenApiConverter(blzDefinitions, "/blz").generateYaml().contains("/get-bank:"));
    }

    @Test
    void blzServiceTitle() {
        var yaml = generator(blzDefinitions, "/blz");

        assertTrue(yaml.contains("title: \"BLZService\""));
        assertTrue(yaml.contains("/get-bank:"));
    }

    @Test
    void citiesProducesGetCityPath() {
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/").generateYaml();

        assertTrue(yaml.contains("paths:"));
        assertTrue(yaml.contains("/get-city:"));
    }

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

    @Test
    void extensionInheritsBaseTypeFields() {
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("baseId:"), "Should contain 'baseId' inherited from BaseType");
        assertTrue(yaml.contains("baseName:"), "Should contain 'baseName' inherited from BaseType");
        assertTrue(yaml.contains("extra:"), "Should contain 'extra' from ExtendedType");
    }

    @Test
    void unboundedElementProducesArraySchema() {
        assertTrue(generator(extendedDefinitions, "/").contains("type: \"array\""));
    }

    @Test
    void choiceAlternativesAreAllPresent() {
        // searchRequest has <xsd:choice> with byName and byId
        var yaml = generator(extendedDefinitions, "/");

        assertTrue(yaml.contains("byName:"), "Should contain 'byName' from choice");
        assertTrue(yaml.contains("byId:"), "Should contain 'byId' from choice");
    }

    @Test
    void namedSimpleTypeRestrictedToStringMapsToString() {
        assertTrue(generator(extendedDefinitions, "/").contains("code:"));
    }

    @Test
    void crossNamespaceTypeIsResolved() {
        var yaml = generator(crossNsDefinitions, "/");

        assertTrue(yaml.contains("itemName:"), "Should resolve ItemType from the types namespace");
        assertTrue(yaml.contains("itemCount:"), "Should resolve ItemType from the types namespace");
    }

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

    @Test
    void externalXsdImportChainResolvesMoneyType() {
        var yaml = generator(articleDefinitions, "/");
        assertTrue(yaml.contains("amount:"), "MoneyType.amount should be resolved from external XSD chain");
        assertTrue(yaml.contains("currency:"), "MoneyType.currency should be resolved from external XSD chain");
    }

    @Test
    void operationTagAppearsInYaml() {
        var settings = new OperationSettings();
        settings.setTag("MyService");
        var ops = Map.of("getCity", settings);
        var yaml = new Wsdl2OpenApiConverter(citiesDefinitions, "/", ops).generateYaml();

        assertTrue(yaml.contains("tags:"), "Should contain tags section");
        assertTrue(yaml.contains("MyService"), "Should contain configured tag value");
        assertTrue(yaml.contains("- name: \"MyService\""), "Top-level tags section should declare the tag by name");
    }

    @Test
    void configuredOperationNotInWsdlIsRejected() {
        var ops = Map.of("getCitty", new OperationSettings());

        var e = assertThrows(ConfigurationException.class,
                () -> new Wsdl2OpenApiConverter(citiesDefinitions, "/", ops).generate());

        assertTrue(e.getMessage().contains("getCitty"), "Message should name the unknown operation");
        assertTrue(e.getMessage().contains("getCity"), "Message should list the available operations");
    }

    @Test
    void noTagWhenNotConfigured() {
        assertFalse(generator(citiesDefinitions, "/").contains("tags:"), "Should have no tags when none configured");
    }

    private static String generator(Definitions defs, String basePath) {
        return new Wsdl2OpenApiConverter(defs, basePath).generateYaml();
    }
}
