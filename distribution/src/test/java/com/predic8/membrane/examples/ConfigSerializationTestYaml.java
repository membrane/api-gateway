package com.predic8.membrane.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ConfigSerializationTestYaml {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.byDefault();

    private static final String SCHEMA_CLASSPATH =
            "/com/predic8/membrane/core/config/json/membrane.schema.json";

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

    public static Stream<String> getYamlConfigs() throws IOException {
        Path root = Paths.get("examples");
        if (!Files.exists(root))
            return Stream.empty();

        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().matches("proxies\\.ya?ml"))
                .map(Path::toString)
                .filter(string -> EXCLUDED.stream().noneMatch(ex -> string.contains("/" + ex + "/")));
    }

    @ParameterizedTest
    @MethodSource("getYamlConfigs")
    public void validateSchema(String yamlPath) throws Exception {
        try {

            Object originalObj = YAML.readValue(Files.readString(Paths.get(yamlPath), UTF_8), Object.class);

            JsonSchema schema;
            try (InputStream in = getClass().getResourceAsStream(SCHEMA_CLASSPATH)) {
                if (in == null)
                    throw new IOException("Schema not found on classpath: " + SCHEMA_CLASSPATH);
                schema = SCHEMA_FACTORY.getJsonSchema(JSON.readTree(in));
            }
            ProcessingReport report = schema.validateUnchecked(JSON.valueToTree(originalObj));
            assertThat("Schema validation failed for " + yamlPath + ":\n" + report,
                    report.isSuccess(), is(true));

            assertThat(JSON.valueToTree(YAML.readValue(YAML.writeValueAsString(originalObj), Object.class)),
                    is(JSON.valueToTree(originalObj)));
        } catch (Exception e) {
            throw new Exception("Failed validating file: " + yamlPath, e);
        }
    }

}
