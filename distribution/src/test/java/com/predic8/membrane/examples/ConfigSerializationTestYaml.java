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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.networknt.schema.InputFormat.JSON;
import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ConfigSerializationTestYaml {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final String SCHEMA_CLASSPATH =
            "/com/predic8/membrane/core/config/json/membrane.schema.json";

    private static final Schema SCHEMA;

    static {
        try (InputStream in = ConfigSerializationTestYaml.class.getResourceAsStream(SCHEMA_CLASSPATH)) {
            if (in == null)
                throw new IOException("Schema not found on classpath: " + SCHEMA_CLASSPATH);
            SCHEMA = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, b -> {})
                    .getSchema(SchemaLocation.of("https://membrane-soa.org/membrane.schema.json"), in, JSON);
            SCHEMA.initializeValidators();
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
                    List<Error> errors = SCHEMA.validate(JSON_MAPPER.valueToTree(it.next()));
                    assertThat("Schema validation failed for " + yamlPath + ":\n" + errors,
                            errors.isEmpty(), is(true));
                }
            }
        } catch (Exception e) {
            throw new Exception("Failed validating file: " + yamlPath, e);
        }
    }
}
