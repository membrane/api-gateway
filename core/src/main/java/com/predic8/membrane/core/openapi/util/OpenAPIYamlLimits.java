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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.parser.util.DeserializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;

/**
 * Lifts the input size ceiling SnakeYAML puts on OpenAPI documents.
 * <p>
 * SnakeYAML rejects any document above {@link LoaderOptions} {@code codePointLimit}, 3 MiB by
 * default, with "The incoming YAML document exceeds the limit". Real OpenAPI documents pass that
 * mark easily once their $refs are inlined, and the limit is enforced in three independent places
 * of which only the second reacts to the {@value #CODE_POINT_LIMIT_PROPERTY} system property
 * swagger-parser documents:
 * <ol>
 *     <li>the mapper Membrane reads the document with itself, see {@link #createYamlMapper()}</li>
 *     <li>swagger-parser's SnakeYAML path, {@code DeserializationUtils.buildLoaderOptions()}</li>
 *     <li>swagger-parser's {@code yaml30Mapper}/{@code yaml31Mapper}, which are built without any
 *         {@code LoaderOptions} at all, so they cap 3.0 and 3.1 documents regardless of the
 *         property, see <a href="https://github.com/swagger-api/swagger-parser/issues/2059">
 *         swagger-parser#2059</a></li>
 * </ol>
 * There is no flag that switches the limit off; it is an int that has to be set high, so the
 * default here is {@link Integer#MAX_VALUE}. Set {@value #CODE_POINT_LIMIT_PROPERTY} to put a
 * bound back in place. Note that this ceiling is only a size guard: protection against the billion
 * laughs attack is a separate setting ({@code maxYamlAliasesForCollections}) that stays untouched.
 */
public class OpenAPIYamlLimits {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIYamlLimits.class.getName());

    /**
     * Read by swagger-parser as well, so one setting covers Membrane and the parser. Read once when
     * this class is loaded, so it has to be set at JVM startup:
     * {@code JAVA_OPTS="-DmaxYamlCodePoints=..."}.
     */
    public static final String CODE_POINT_LIMIT_PROPERTY = "maxYamlCodePoints";

    private static final int codePointLimit = readCodePointLimit();

    private static final YAMLFactory yamlFactory = createYamlFactory();

    static {
        // Global state of swagger-parser. Has to happen before the first document is parsed, which
        // holds because the class is loaded when the first mapper is created.
        DeserializationUtils.getOptions().setMaxYamlCodePoints(codePointLimit);
        DeserializationUtils.setYaml30Mapper(yamlFactory);
        DeserializationUtils.setYaml31Mapper(yamlFactory);
    }

    private OpenAPIYamlLimits() {
    }

    /**
     * A YAML mapper that does not cap the document size, to be used instead of
     * {@code ObjectMapperFactory.createYaml()} wherever an OpenAPI document is read.
     */
    public static ObjectMapper createYamlMapper() {
        return new ObjectMapper(yamlFactory);
    }

    public static int getCodePointLimit() {
        return codePointLimit;
    }

    private static YAMLFactory createYamlFactory() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(codePointLimit);
        return YAMLFactory.builder().loaderOptions(options).build();
    }

    private static int readCodePointLimit() {
        String value = System.getProperty(CODE_POINT_LIMIT_PROPERTY);
        if (value == null)
            return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Ignoring {}={}, not a number. Using {} instead.", CODE_POINT_LIMIT_PROPERTY, value, Integer.MAX_VALUE);
            return Integer.MAX_VALUE;
        }
    }
}
