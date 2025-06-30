/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.model.Body;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.util.URIFactory;
import jakarta.mail.internet.ParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.TestUtils.om;
import static com.predic8.membrane.core.openapi.util.TestUtils.parseOpenAPI;
import static java.util.regex.Pattern.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the OpenAPI validator with the official tests from the JSON Schema Test Suite.
 *
 * The test suite needs to be downloaded manually.
 */
public class JsonSchemaTestSuiteTests {
    private final static Logger log = LoggerFactory.getLogger(JsonSchemaTestSuiteTests.class);
    public final String TEST_SUITE_BASE_PATH = "git\\JSON-Schema-Test-Suite\\tests\\draft6";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    int correct, incorrect, ignored;

    @Disabled("The test requires a manual download. It also fails.")
    @Test
    void jsonSchema() throws IOException, ParseException {
        runTestsFoundInDirectory(TEST_SUITE_BASE_PATH);
        log.info("correct = {}", correct);
        log.info("incorrect = {}", incorrect);
        log.info("ignored = {}", ignored);

        assertEquals(0, incorrect);
    }

    private void runTestsFoundInDirectory(String baseDir) throws IOException, ParseException {
        File base = new File(baseDir);
        if (!base.exists()) {
            throw new RuntimeException("Please download the tests from https://github.com/json-schema-org/JSON-Schema-Test-Suite/ and adjust the base path here.");
        }
        for (File file : base.listFiles()) {
            if (!file.getName().endsWith(".json"))
                continue;
            runTestsFromFile(file);
        }
    }

    private void runTestsFromFile(File file) throws IOException, ParseException {
        log.info("Testing file: {}", file.getName());

        List<?> tests = objectMapper.readValue(file, List.class);
        for (Object t : tests) {
            Map test = (Map) t;
            String description = test.get("description").toString();
            Object schema = test.get("schema");
            List<?> testRuns = (List<?>) test.get("tests");

            log.info("- description = {}", description);
            log.info("  schema = {}", om.writeValueAsString(schema));

            String openapi = generateOpenAPIForSchema(schema);

            String ignoredReason = computeIgnoredReason(openapi, description);

            OpenAPIValidator validator = null;
            if (ignoredReason == null)
                validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(parseOpenAPI(
                        new ByteArrayInputStream(openapi.getBytes(StandardCharsets.UTF_8))),new OpenAPISpec()));

            for (Object tr : testRuns)
                runSingleTestRun((Map) tr, ignoredReason, validator);
        }
    }

    private @NotNull String generateOpenAPIForSchema(Object schema) throws JsonProcessingException {
        Map openapi = new HashMap(of("openapi", "3.1.0", "paths", of("/test", of("post", of(
                "requestBody", of("content", of("application/json", of("schema", schema))),
                "responses", of("200", of("description", "OK")))))));

        moveTypeDefinitions(schema, openapi, "definitions");
        moveTypeDefinitions(schema, openapi, "$defs");

        return replaceReferences(replaceReferences(yamlMapper.writeValueAsString(openapi), "definitions"), "$defs");
    }

    private String replaceReferences(String openApiString, String rootKey) {
        return openApiString.replaceAll("#/" + quote(rootKey) + "/", "#/components/schemas/");
    }

    private static void moveTypeDefinitions(Object schema, Map openApi, String rootKey) {
        if (schema instanceof Map && ((Map) schema).containsKey(rootKey)) {
            openApi.put("components", of("schemas", ((Map) schema).get(rootKey)));
            log.warn("    warning: The schema contains definitions. They have been moved from #/" + rootKey + "/ to #/components/schemas/ .");
        }
    }


    private static @Nullable String computeIgnoredReason(String openapi, String description) {
        if (openapi.contains("http://"))
            return "the official test code seems to start a webserver on localhost:1234, which we do not support (yet).";
        if (description.equals("Location-independent identifier"))
            return "'$ref':'#foo' is used, which we do not support.";
        if (description.contains("empty tokens in $ref json-pointer"))
            return "the name of a definition is the empty string.";
        if (description.equals("relative pointer ref to array"))
            return "prefix reference migration not implemented";
        if (description.equals("relative pointer ref to object"))
            return "reference migration not implemented";
        return null;
    }

    private void runSingleTestRun(Map tr, String ignoredReason, OpenAPIValidator validator) throws JsonProcessingException, ParseException {
        Map testRun = tr;

        log.info("  - testRun = {}", om.writeValueAsString(testRun));

        String description = testRun.get("description").toString();
        String body = objectMapper.writeValueAsString(testRun.get("data"));
        Boolean valid = (Boolean)testRun.get("valid");

        log.info("    testRun.description = {}", description);
        log.info("    testRun.body = {}", body);
        log.info("    testRun.shouldBeValid = {}", valid);

        if (ignoredReason != null) {
            ignored++;
            log.info("    Test: Ignored. ({})", ignoredReason);
            return;
        }

        Request<Body> post = post().path("/test").mediaType("application/json");
        ValidationErrors errors = null;
        try {
            errors = validator.validate(post.body(body));

            log.info("    validation result = {}", errors);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (errors != null && errors.isEmpty() == valid) {
            log.info("    Test: OK");
            correct++;
        } else {
            log.warn("    Test: NOT OK!");
            incorrect++;
        }
    }
}
