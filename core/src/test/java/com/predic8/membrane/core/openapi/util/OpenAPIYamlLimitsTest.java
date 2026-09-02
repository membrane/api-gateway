/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.ObjectMapperFactory;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnakeYAML caps YAML input at 3 MiB by default. See {@link OpenAPIYamlLimits}.
 */
public class OpenAPIYamlLimitsTest {

    private static final int THREE_MIB = 3 * 1024 * 1024;

    /**
     * Documents the default we are working around: the mapper Membrane used before refuses a
     * document above 3 MiB. Should this start passing, swagger-parser changed its default and
     * {@link OpenAPIYamlLimits} can be revisited.
     */
    @Test
    void defaultMapperRejectsDocumentAboveThreeMebibytes() {
        ObjectMapper unbounded = ObjectMapperFactory.createYaml();
        assertThrows(Exception.class, () -> unbounded.readTree(openAPIExceeding(THREE_MIB)));
    }

    @Test
    void readsDocumentAboveThreeMebibytes() throws IOException {
        JsonNode node = OpenAPIYamlLimits.createYamlMapper().readTree(openAPIExceeding(THREE_MIB));

        assertEquals("3.0.2", node.get("openapi").asText());
        assertTrue(node.get("paths").size() > 0);
    }

    @Test
    void codePointLimitIsLifted() {
        assertTrue(OpenAPIYamlLimits.getCodePointLimit() > THREE_MIB);
    }

    /**
     * Reading the document is only the first of the three places the limit is enforced. This covers
     * the other two, inside swagger-parser.
     */
    @Test
    void parserReadsDocumentAboveThreeMebibytes() {
        OpenAPIYamlLimits.createYamlMapper(); // loads the class, which configures swagger-parser

        SwaggerParseResult result = new OpenAPIParser().readContents(openAPIExceeding(THREE_MIB), null, new ParseOptions());

        assertNotNull(result.getOpenAPI(), "Parser returned no OpenAPI, messages: " + result.getMessages());
        assertEquals("Large API", result.getOpenAPI().getInfo().getTitle());
        assertTrue(result.getOpenAPI().getPaths().size() > 0);
    }

    /**
     * A valid OpenAPI document padded with generated paths until it is larger than {@code minSize}.
     * Generated rather than committed so the repository stays free of a multi megabyte fixture.
     */
    private static String openAPIExceeding(int minSize) {
        StringBuilder sb = new StringBuilder("""
                openapi: '3.0.2'
                info:
                  title: Large API
                  version: '1.0'
                paths:
                """);
        for (int i = 0; sb.length() <= minSize; i++) {
            sb.append("""
                      /path-%d:
                        get:
                          description: %s
                          responses:
                            '200':
                              description: OK
                    """.formatted(i, "padding ".repeat(20)));
        }
        return sb.toString();
    }
}
