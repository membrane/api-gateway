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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static com.predic8.membrane.core.openapi.util.TestUtils.om;
import static com.predic8.membrane.core.openapi.util.TestUtils.parseOpenAPI;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the OpenAPI validator with the official tests from the JSON Schema Test Suite.
 *
 * The test suite needs to be downloaded manually.
 */
public class JsonSchemaTestSuiteTests {
    public final String TEST_SUITE_BASE_PATH = "git\\JSON-Schema-Test-Suite\\tests\\draft4";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    int correct, incorrect, ignored;

    @Disabled("The test requires a manual download. It also fails.")
    @Test
    public void testJsonSchema() throws IOException, ParseException {
        runTestsFoundInDirectory(TEST_SUITE_BASE_PATH);
        System.out.println("correct = " + correct);
        System.out.println("incorrect = " + incorrect);
        System.out.println("ignored = " + ignored);

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
        System.out.println("Testing file: " + file.getName());

        List<?> tests = objectMapper.readValue(file, List.class);
        for (Object t : tests) {
            Map test = (Map) t;
            String description = test.get("description").toString();
            Object schema = test.get("schema");
            List<?> testRuns = (List<?>) test.get("tests");

            System.out.println("- description = " + description);
            System.out.println("  schema = " + om.writeValueAsString(schema));

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
        Map oa = new HashMap(of("openapi", "3.0.2", "paths", of("/test", of("post", of(
                "requestBody", of("content", of("application/json", of("schema", schema))),
                "responses", of("200", of("description", "OK")))))));

        if (((Map) schema).containsKey("definitions")) {
            oa.put("components", of("schemas", ((Map) schema).get("definitions")));
            System.out.println("    warning: The schema contains definitions. They have been moved from #/definitions/ to #/components/schemas/ .");
        }

        String openapi = yamlMapper.writeValueAsString(oa);

        openapi = openapi.replaceAll("#/definitions/", "#/components/schemas/");
        return openapi;
    }

    private static @Nullable String computeIgnoredReason(String openapi, String description) {
        if (openapi.contains("http://"))
            return "the official test code seems to start a webserver on localhost:1234, which we do not support (yet).";
        if (description.equals("Location-independent identifier"))
            return "'$ref':'#foo' is used, which we do not support.";
        if (description.contains("empty tokens in $ref json-pointer"))
            return "the name of a definition is the empty string.";
        return null;
    }

    private void runSingleTestRun(Map tr, String ignoredReason, OpenAPIValidator validator) throws JsonProcessingException, ParseException {
        Map testRun = tr;

        System.out.println("  - testRun = " + om.writeValueAsString(testRun));

        String description2 = testRun.get("description").toString();
        String body = objectMapper.writeValueAsString(testRun.get("data"));
        Boolean valid = (Boolean)testRun.get("valid");

        System.out.println("    testRun.description = " + description2);
        System.out.println("    testRun.body = " + body);
        System.out.println("    testRun.shouldBeValid = " + valid);

        if (ignoredReason != null) {
            ignored++;
            System.out.println("    Test: Ignored. (" + ignoredReason + ")");
            return;
        }

        Request<Body> post = Request.post().path("/test").mediaType("application/json");
        ValidationErrors errors = null;
        try {
            errors = validator.validate(post.body(body));

            System.out.println("    validation result = " + errors);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (errors != null && errors.isEmpty() == valid) {
            System.out.println("    Test: OK");
            correct++;
        } else {
            System.out.println("    Test: NOT OK!");
            incorrect++;
        }
    }
}
