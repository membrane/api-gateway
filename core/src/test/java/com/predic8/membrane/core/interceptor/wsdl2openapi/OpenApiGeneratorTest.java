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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiGeneratorTest {

    static Definitions citiesDefinitions;
    static Definitions blzDefinitions;

    @BeforeAll
    static void setup() throws Exception {
        citiesDefinitions = Definitions.parse(new ResolverMap(), "classpath:/ws/cities.wsdl");
        blzDefinitions = Definitions.parse(new ResolverMap(), "classpath:/blz-service.wsdl");
    }

    @Test
    void openApiHeader() {
        var yaml = generator(citiesDefinitions, "/purchasing", "getCity");

        assertTrue(yaml.contains("openapi: \"3.0.0\""), "Should contain openapi version");
        assertTrue(yaml.contains("info:"), "Should contain info section");
        assertTrue(yaml.contains("version: \"1.0.0\""), "Should contain version");
    }

    @Test
    void serviceNameUsedAsTitle() {
        var yaml = generator(citiesDefinitions, "/purchasing", "getCity");

        assertTrue(yaml.contains("title: \"CityService\""), "Title should use WSDL service name");
    }

    @Test
    void serverUrlFromBasePath() {
        var yaml = generator(citiesDefinitions, "/purchasing", "getCity");

        assertTrue(yaml.contains("url: \"/purchasing\""), "Should contain the base path as server URL");
    }

    @Test
    void basePathTrailingSlashStripped() {
        var yaml = new OpenApiGenerator(citiesDefinitions, "/purchasing/", operationsMap("getCity")).generateYaml();

        assertTrue(yaml.contains("url: \"/purchasing\""), "Trailing slash should be stripped from server URL");
        assertFalse(yaml.contains("url: \"/purchasing/\""), "Should not contain trailing slash");
    }

    @Test
    void camelCaseOperationMappedToKebabPath() {
        var yaml = generator(citiesDefinitions, "/", "getCity");

        assertTrue(yaml.contains("/get-city:"), "getCity should become /get-city path");
    }

    @Test
    void operationUsesPostMethod() {
        var yaml = generator(citiesDefinitions, "/", "getCity");

        assertTrue(yaml.contains("post:"), "Operation should use POST method");
        assertFalse(yaml.contains("get:"), "Should not generate GET endpoint");
    }

    @Test
    void operationIdMatchesOriginalCamelCase() {
        var yaml = generator(citiesDefinitions, "/", "getCity");

        assertTrue(yaml.contains("operationId: \"getCity\""), "operationId should use original camelCase name");
    }

    @Test
    void requestBodyIsRequired() {
        var yaml = generator(citiesDefinitions, "/", "getCity");

        assertTrue(yaml.contains("required: true"), "requestBody should be required");
        assertTrue(yaml.contains("application/json:"), "Should accept application/json");
    }

    @Test
    void responsesWith200And500() {
        var yaml = generator(citiesDefinitions, "/", "getCity");

        assertTrue(yaml.contains("\"200\":"), "Should define 200 response");
        assertTrue(yaml.contains("\"500\":"), "Should define 500 response");
    }

    @Test
    void multipleOperationsGenerateMultiplePaths() {
        Map<String, OperationConfig> ops = new LinkedHashMap<>();
        ops.put("getOrders", new OperationConfig());
        ops.put("createOrder", new OperationConfig());
        ops.put("deleteOrder", new OperationConfig());

        var yaml = new OpenApiGenerator(citiesDefinitions, "/purchasing", ops).generateYaml();

        assertTrue(yaml.contains("/get-orders:"), "Should contain /get-orders path");
        assertTrue(yaml.contains("/create-order:"), "Should contain /create-order path");
        assertTrue(yaml.contains("/delete-order:"), "Should contain /delete-order path");
    }

    @Test
    void blzServiceTitle() {
        var yaml = generator(blzDefinitions, "/blz", "getBank");

        assertTrue(yaml.contains("title: \"BLZService\""), "Title should use BLZ service name");
        assertTrue(yaml.contains("/get-bank:"), "getBank should become /get-bank path");
    }

    @Test
    void emptyOperationsProducesEmptyPaths() {
        var yaml = new OpenApiGenerator(citiesDefinitions, "/", new LinkedHashMap<>()).generateYaml();

        assertTrue(yaml.contains("paths:"), "Should still contain paths section");
    }

    private static String generator(Definitions defs, String basePath, String... ops) {
        return new OpenApiGenerator(defs, basePath, operationsMap(ops)).generateYaml();
    }

    private static Map<String, OperationConfig> operationsMap(String... names) {
        Map<String, OperationConfig> map = new LinkedHashMap<>();
        for (String name : names) {
            map.put(name, new OperationConfig());
        }
        return map;
    }
}
