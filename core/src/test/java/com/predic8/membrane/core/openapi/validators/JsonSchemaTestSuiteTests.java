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
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import com.predic8.membrane.core.util.URIFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.google.common.collect.ImmutableMap.of;
import static com.predic8.membrane.core.openapi.model.Request.post;
import static com.predic8.membrane.core.openapi.util.OpenAPITestUtils.parseOpenAPI;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.quote;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the OpenAPI validator with the official tests from the JSON Schema Test Suite.
 * <p>
 * The test suite needs to be downloaded manually from https://github.com/json-schema-org/JSON-Schema-Test-Suite/.
 * Run with <code>-DjsonSchemaTestSuite.dir=&lt;path to the suite&gt;</code>. The JSON Schema draft defaults to
 * draft2020-12, the dialect of OpenAPI 3.1; another one can be chosen with <code>-DjsonSchemaTestSuite.draft</code>.
 * The results are logged and written as a Markdown report to <code>target/json-schema-test-suite/</code>.
 */
@EnabledIfSystemProperty(named = "jsonSchemaTestSuite.dir", matches = ".+")
public class JsonSchemaTestSuiteTests {
    private final static Logger log = LoggerFactory.getLogger(JsonSchemaTestSuiteTests.class);

    private static final int MAX_DETAIL_LENGTH = 300;

    private static final String ROOT_SCHEMA = "jsonSchemaTestSuiteRoot";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    private final String draft = System.getProperty("jsonSchemaTestSuite.draft", "draft2020-12");

    /**
     * Results per test file, i.e. per keyword, in file name order.
     */
    private final Map<String, FileResult> results = new TreeMap<>();

    enum Outcome {PASSED, FAILED, ERROR, IGNORED}

    record TestRunResult(String group, String description, String data, boolean expectedValid, Outcome outcome,
                         String detail) {
    }

    static class FileResult {
        final List<TestRunResult> runs = new ArrayList<>();

        long count(Outcome outcome) {
            return runs.stream().filter(r -> r.outcome() == outcome).count();
        }
    }

    @Test
    void jsonSchema() throws IOException {
        final var base = Path.of(System.getProperty("jsonSchemaTestSuite.dir"), "tests", draft);
        if (!Files.isDirectory(base))
            throw new RuntimeException("%s not found. Download the tests from https://github.com/json-schema-org/JSON-Schema-Test-Suite/ and pass its directory as -DjsonSchemaTestSuite.dir.".formatted(base));

        runTestsFoundInDirectory(base.toFile());

        final var report = createReport();
        final var reportFile = Path.of("target", "json-schema-test-suite", draft + ".md");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, report, UTF_8);
        log.info("\n{}", createSummary());
        log.info("Report written to {}", reportFile.toAbsolutePath());

        assertEquals(0, total(Outcome.FAILED) + total(Outcome.ERROR), "Failed or erroneous test runs, see " + reportFile.toAbsolutePath());
    }

    private void runTestsFoundInDirectory(File base) throws IOException {
        final var files = Objects.requireNonNull(base.listFiles((dir, name) -> name.endsWith(".json")));
        Arrays.sort(files);
        for (File file : files)
            runTestsFromFile(file);
    }

    @SuppressWarnings("rawtypes")
    private void runTestsFromFile(File file) throws IOException {
        log.info("Testing file: {}", file.getName());
        final var fileResult = results.computeIfAbsent(file.getName(), k -> new FileResult());

        final List<?> tests = objectMapper.readValue(file, List.class);
        for (Object t : tests) {
            final var test = (Map) t;
            final var group = test.get("description").toString();
            final var schema = test.get("schema");
            final var testRuns = (List<?>) test.get("tests");

            final var openapi = generateOpenAPIForSchema(schema);
            log.debug("- {}\n{}", group, openapi);

            var ignoredReason = computeIgnoredReason(openapi, group);
            OpenAPIValidator validator = null;
            if (ignoredReason == null) {
                try {
                    validator = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(parseOpenAPI(
                            new ByteArrayInputStream(openapi.getBytes(UTF_8))), new OpenAPISpec()));
                } catch (Exception e) {
                    // Every test run of the group is reported with the exception
                    for (Object tr : testRuns)
                        fileResult.runs.add(result(group, (Map) tr, Outcome.ERROR, "Could not create validator: " + describe(e)));
                    continue;
                }
            }

            for (Object tr : testRuns)
                fileResult.runs.add(runSingleTestRun(group, (Map) tr, ignoredReason, validator));
        }
    }

    private @NotNull String generateOpenAPIForSchema(Object schema) throws JsonProcessingException {
        Map openapi = new HashMap(of("openapi", "3.1.0",
                "info", of("title", "JSON Schema Test Suite", "version", "1.0"),
                "paths", of("/test", of("post", of(
                        "requestBody", of("content", of("application/json", of("schema", schema))),
                        "responses", of("200", of("description", "OK")))))));

        final var schemas = new HashMap<String, Object>();
        moveTypeDefinitions(schema, schemas, "definitions");
        moveTypeDefinitions(schema, schemas, "$defs");
        // "#" would point to the root of the OpenAPI document, so the schema is also made available as a component
        final var refersToRoot = objectMapper.writeValueAsString(schema).contains("\"$ref\":\"#\"");
        if (refersToRoot)
            schemas.put(ROOT_SCHEMA, schema);
        if (!schemas.isEmpty())
            openapi.put("components", of("schemas", schemas));

        var yaml = replaceReferences(replaceReferences(yamlMapper.writeValueAsString(openapi), "definitions"), "$defs");
        if (refersToRoot)
            yaml = yaml.replace("$ref: \"#\"", "$ref: \"#/components/schemas/" + ROOT_SCHEMA + "\"");
        return yaml;
    }

    private String replaceReferences(String openApiString, String rootKey) {
        return openApiString.replaceAll("#/" + quote(rootKey) + "/", "#/components/schemas/");
    }

    @SuppressWarnings("unchecked")
    private static void moveTypeDefinitions(Object schema, Map<String, Object> schemas, String rootKey) {
        if (schema instanceof Map && ((Map) schema).containsKey(rootKey)) {
            schemas.putAll((Map<String, Object>) ((Map) schema).get(rootKey));
            log.debug("The schema contains definitions. They have been moved from #/{}/ to #/components/schemas/ .", rootKey);
        }
    }

    private static @Nullable String computeIgnoredReason(String openapi, String description) {
        if (openapi.contains("http://localhost:1234"))
            return "remote references to localhost:1234, which the official test code serves, are not supported (yet).";
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

    @SuppressWarnings("rawtypes")
    private TestRunResult runSingleTestRun(String group, Map testRun, String ignoredReason, OpenAPIValidator validator) throws JsonProcessingException {
        if (ignoredReason != null)
            return result(group, testRun, Outcome.IGNORED, ignoredReason);

        final var valid = (Boolean) testRun.get("valid");
        try {
            final var errors = validator.validate(post().path("/test").mediaType("application/json")
                    .body(objectMapper.writeValueAsString(testRun.get("data"))));
            if (errors.isEmpty() == valid)
                return result(group, testRun, Outcome.PASSED, null);
            return result(group, testRun, Outcome.FAILED, valid ? "rejected: " + errors : "accepted");
        } catch (Exception e) {
            return result(group, testRun, Outcome.ERROR, describe(e));
        }
    }

    @SuppressWarnings("rawtypes")
    private TestRunResult result(String group, Map testRun, Outcome outcome, String detail) throws JsonProcessingException {
        final var r = new TestRunResult(group, testRun.get("description").toString(),
                objectMapper.writeValueAsString(testRun.get("data")), (Boolean) testRun.get("valid"), outcome, detail);
        if (outcome == Outcome.FAILED || outcome == Outcome.ERROR)
            log.debug("{}: {} / {} -> {} {}", outcome, group, r.description(), r.data(), detail);
        return r;
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private long total(Outcome outcome) {
        return results.values().stream().mapToLong(r -> r.count(outcome)).sum();
    }

    private String createSummary() {
        final var sb = new StringBuilder();
        final var executed = total(Outcome.PASSED) + total(Outcome.FAILED) + total(Outcome.ERROR);
        sb.append("JSON Schema Test Suite, %s: %d passed, %d failed, %d errors, %d ignored (%s of the executed test runs passed)\n\n"
                .formatted(draft, total(Outcome.PASSED), total(Outcome.FAILED), total(Outcome.ERROR), total(Outcome.IGNORED),
                        percent(total(Outcome.PASSED), executed)));
        sb.append("| Keyword | Passed | Failed | Errors | Ignored | Pass rate |\n");
        sb.append("|---|---:|---:|---:|---:|---:|\n");
        results.forEach((file, r) -> {
            final var passed = r.count(Outcome.PASSED);
            sb.append("| %s | %d | %d | %d | %d | %s |\n".formatted(file.replace(".json", ""), passed,
                    r.count(Outcome.FAILED), r.count(Outcome.ERROR), r.count(Outcome.IGNORED),
                    percent(passed, passed + r.count(Outcome.FAILED) + r.count(Outcome.ERROR))));
        });
        return sb.toString();
    }

    private String createReport() {
        final var sb = new StringBuilder("# JSON Schema Test Suite: %s\n\n".formatted(draft));
        sb.append(createSummary());
        appendDetails(sb, "Failures", Outcome.FAILED);
        appendDetails(sb, "Errors", Outcome.ERROR);
        appendDetails(sb, "Ignored", Outcome.IGNORED);
        return sb.toString();
    }

    private void appendDetails(StringBuilder sb, String title, Outcome outcome) {
        if (total(outcome) == 0)
            return;
        sb.append("\n## %s\n".formatted(title));
        results.forEach((file, r) -> {
            final var runs = r.runs.stream().filter(run -> run.outcome() == outcome).toList();
            if (runs.isEmpty())
                return;
            sb.append("\n### %s\n\n".formatted(file.replace(".json", "")));
            for (var run : runs)
                sb.append("- **%s** › %s: `%s` should be %s%s\n".formatted(run.group(), run.description(),
                        truncate(run.data()), run.expectedValid() ? "valid" : "invalid",
                        outcome == Outcome.IGNORED ? " (" + oneLine(run.detail()) + ")" : ", but " + oneLine(run.detail())));
        });
    }

    private static String percent(long part, long whole) {
        return whole == 0 ? "-" : "%.0f %%".formatted(100.0 * part / whole);
    }

    private static String oneLine(String s) {
        return truncate(s.replaceAll("\\s+", " "));
    }

    private static String truncate(String s) {
        return s.length() <= MAX_DETAIL_LENGTH ? s : s.substring(0, MAX_DETAIL_LENGTH) + "…";
    }
}
