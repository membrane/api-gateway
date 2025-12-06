package com.predic8.membrane.annot.yaml;

import com.networknt.schema.path.NodePath;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.networknt.schema.Error.builder;
import static com.networknt.schema.path.PathType.JSON_POINTER;
import static com.predic8.membrane.annot.yaml.YamlSchemaValidationException.*;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

class YamlSchemaValidationExceptionTest {

    @Nested
    public class BasePathTests {
        @Test
        public void demo() {
            assertEquals("/demo",
                    getBasePath(builder().evaluationPath(np().append("demo")).build()));
        }

        @Test
        public void additionalProperties() {
            assertEquals("/additionalProperties",
                    getBasePath(builder().evaluationPath(np().append("additionalProperties")).build()));
        }

        @Test
        public void numberAndAdditionalProperties() {
            assertEquals("",
                    getBasePath(builder().evaluationPath(np().append(12).append("additionalProperties")).build()));
        }

        @Test
        public void demoAndNumberAndAdditionalProperties() {
            assertEquals("/demo",
                    getBasePath(builder().evaluationPath(np().append("demo").append(12).append("additionalProperties")).build()));
        }

        @Test
        public void additionalPropertiesInTheMiddle() {
            assertEquals("/demo/12/additionalProperties/bar",
                    getBasePath(builder().evaluationPath(np().append("demo").append(12).append("additionalProperties").append("bar")).build()));
        }
    }

    @Nested
    public class ExtractNumberBeforeAdditionalPropertiesTests {
        @Test
        public void demoAndNumberAndAdditionalProperties() {
            assertEquals(12,
                    extractNumberBeforeAdditionalProperties(builder().evaluationPath(np().append("demo").append(12).append("additionalProperties")).build()));
        }

        @Test
        public void numberAndAdditionalProperties() {
            assertEquals(12,
                    extractNumberBeforeAdditionalProperties(builder().evaluationPath(np().append(12).append("additionalProperties")).build()));
        }
    }

    @Nested
    public class StripTest {
        @Test
        public void dropAllHighFrequencyAdditionalProperties() {
            assertEquals(
                    1,
                    shortenErrorList(of(
                            builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build()
                    )).size()
            );

            assertEquals(
                    2,
                    shortenErrorList(of(
                            builder().evaluationPath(np().append("bar")).build(),
                            builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build()
                    )).size()
            );
        }
        @Test
        public void dropOneOf() {
            assertEquals(
                    1,
                    shortenErrorList(of(
                            builder().evaluationPath(np().append("something")).build(),
                            builder().evaluationPath(np().append("oneOf").append("a")).build(),
                            builder().evaluationPath(np().append("oneOf").append("b")).build()
                    )).size()
            );

            assertEquals(
                    2,
                    shortenErrorList(of(
                            builder().evaluationPath(np().append("oneOf").append("a")).build(),
                            builder().evaluationPath(np().append("oneOf").append("b")).build()
                    )).size()
            );
        }
    }

    private static @NotNull NodePath np() {
        return new NodePath(JSON_POINTER);
    }
}