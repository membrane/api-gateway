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

package com.predic8.membrane.annot.yaml;

import com.networknt.schema.path.NodePath;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public class FilterByNumberBeforeAdditionalPropertiesTests {
        @Test
        public void run() {
            assertEquals(1,
                filterByNumberBeforeAdditionalProperties(of(
                        builder().evaluationPath(np().append(3).append("additionalProperties")).build(), // kept
                        builder().evaluationPath(np().append(4).append("additionalProperties")).build(), // dropped
                        builder().evaluationPath(np().append(4).append("additionalProperties")).build() // dropped
                ), Set.of(3)).size());
        }
    }

    @Nested
    public class GetKeysWithLowestValuesTest {
        @Test
        public void run() {
            assertEquals(Set.of(12), getKeysWithLowestValues(Map.of(12, 1L, 13, 2L)));
            assertEquals(Set.of(13), getKeysWithLowestValues(Map.of(12, 3L, 13, 2L)));
            assertEquals(Set.of(12, 14), getKeysWithLowestValues(Map.of(12, 1L, 13, 2L, 14, 1L)));
        }
    }

    @Nested
    public class CollectFrequenciesOfNumberBeforeAdditionalPropertiesTest {
        @Test
        public void run() {
            assertEquals(Map.of(-1, 1L, 1, 1L, 2, 2L),
                collectFrequenciesOfNumberBeforeAdditionalProperties(of(
                        builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                        builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                        builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                        builder().evaluationPath(np().append("$ref").append("additionalProperties")).build()
                )));
        }
    }

    @Nested
    public class GroupByBasePathTest {
        @Test
        public void singleGroup() {
            assertEquals(
                    of(of(
                            builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append("$ref").append("additionalProperties")).build())),
                    groupByBasePath(of(
                            builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append("$ref").append("additionalProperties")).build()
                    )).collect(Collectors.toUnmodifiableList()));
        }

        @Test
        public void twoGroups() {
            assertEquals(
                    of(
                            of(
                                builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                                builder().evaluationPath(np().append(2).append("additionalProperties")).build()),
                            of(
                                builder().evaluationPath(np().append("bar").append(2).append("additionalProperties")).build(),
                                builder().evaluationPath(np().append("bar").append("$ref").append("additionalProperties")).build())),
                    groupByBasePath(of(
                            builder().evaluationPath(np().append(1).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append("bar").append(2).append("additionalProperties")).build(),
                            builder().evaluationPath(np().append("bar").append("$ref").append("additionalProperties")).build()
                    )).collect(Collectors.toUnmodifiableList()));
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