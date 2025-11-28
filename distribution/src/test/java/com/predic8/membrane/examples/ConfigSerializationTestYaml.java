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

package com.predic8.membrane.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ConfigSerializationTestYaml {

    private static final tools.jackson.databind.ObjectMapper YAML =
            YAMLMapper.builder().build();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.byDefault();

    private static final String SCHEMA_CLASSPATH =
            "/com/predic8/membrane/core/config/json/membrane.schema.json";

    private static final JsonSchema SCHEMA;

    static {
        try (InputStream in = ConfigSerializationTestYaml.class.getResourceAsStream(SCHEMA_CLASSPATH)) {
            if (in == null)
                throw new IOException("Schema not found on classpath: " + SCHEMA_CLASSPATH);
            SCHEMA = SCHEMA_FACTORY.getJsonSchema(JSON.readTree(in));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON schema", e);
        }
    }

    public static final List<String> EXCLUDED = asList("custom-interceptor",
            "custom-websocket-interceptor",
            "jdbc-database",
            "proxy",
            "custom-interceptor-maven",
            "stax-interceptor",
            "soap",
            "access",
            "opentelemetry",
            "basic-xml-interceptor",
            "database",
            "simple",
            "docker",
            "openapi-proxy",
            "validation-security",
            "validation",
            "validation-simple",
            "template",
            "greasing");

    public static Stream<String> getYamlConfigs() {
        List<String> roots = List.of("examples", "router");
        List<String> configs = roots.stream()
                .map(Paths::get)
                .filter(Files::exists)
                .flatMap(root -> {
                    try {
                        try (Stream<Path> paths = Files.walk(root)) {
                            return paths.filter(Files::isRegularFile)
                                    .filter(p -> p.getFileName().toString().matches("proxies\\.ya?ml"))
                                    .map(Path::toString)
                                    .toList().stream();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).filter(string -> EXCLUDED.stream().noneMatch(ex -> string.contains("/" + ex + "/"))).toList();

        return configs.stream();
    }

    @ParameterizedTest
    @MethodSource("getYamlConfigs")
    public void validateSchema(String yamlPath) throws Exception {
        try {
            try (Reader reader = Files.newBufferedReader(Paths.get(yamlPath), UTF_8)) {
                MappingIterator<Object> it = YAML.readerFor(Object.class).readValues(reader);

                while (it.hasNext()) {
                    ProcessingReport report = SCHEMA.validateUnchecked(JSON.valueToTree(it.next()));
                    assertThat("Schema validation failed for " + yamlPath + ":\n" + report,
                            report.isSuccess(), is(true));

                    report.iterator().forEachRemaining(pm -> {
                        // the fge library attempts to resolve the URL from the top level "id" field,
                        // we therefore changed the schema to 06 and "id" to "$id" which is ignored by the library
                        String pmStr = pm.toString();
                        if (pmStr.contains("{\"loadingURI\":\"#\",\"pointer\":\"\"}") && pmStr.contains("the following keywords are unknown and will be ignored: [$id]"))
                            return;
                        // the library also complains about the IntelliJ specific HTML description, which is fine.
                        if (pmStr.contains("x-intellij-html-description"))
                            return;
                        throw new RuntimeException("Schema validation failed for " + yamlPath + ":\n" + pm);
                    });
                }
            }
        } catch (Exception e) {
            throw new Exception("Failed validating file: " + yamlPath, e);
        }
    }
}
