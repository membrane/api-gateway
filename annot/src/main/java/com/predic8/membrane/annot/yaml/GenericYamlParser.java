/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.predic8.membrane.annot.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import static com.networknt.schema.SpecificationVersion.*;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.MethodSetter.*;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.UUID.*;

public class GenericYamlParser {
    private static final Logger log = LoggerFactory.getLogger(GenericYamlParser.class);
    private static final String EMPTY_DOCUMENT_WARNING = "Skipping empty document. Maybe there are two --- separators but no configuration in between.";

    private final List<BeanDefinition> beanDefs = new ArrayList<>();

    /**
     * Parses one or more YAML documents into bean definitions.
     * <p>
     * The input string may contain multiple YAML documents separated by '---'. Each non-empty
     * document is validated against the schema provided by {@link Grammar} and then
     * turned into a {@link BeanDefinition}. Validation errors are mapped back to line/column
     * numbers using {@link JsonLocationMap} to produce helpful error messages.
     * </p>
     * @param grammar provides schema location and Java type resolution
     * @param yaml the raw YAML content (may contain multi-document stream)
     * @throws IOException if schema loading or validation fails
     */
    public GenericYamlParser(Grammar grammar, String yaml) throws IOException {
        JsonLocationMap jsonLocationMap = new JsonLocationMap();
        List<JsonNode> rootNodes = jsonLocationMap.parseWithLocations(yaml);

        var idx = 0;
        for (JsonNode jsonNode : rootNodes) {
            if (jsonNode == null) {
                log.debug(GenericYamlParser.EMPTY_DOCUMENT_WARNING);
                continue;
            }

            // Validate YAML against JSON schema
            try {
                validate(grammar, jsonNode);
            } catch (YamlSchemaValidationException e) {
                TokenStreamLocation location = jsonLocationMap.getLocationMap().get(
                        e.getErrors().getFirst().getInstanceNode());
                throw new IOException("Invalid YAML: %s at line %d, column %d.".formatted(
                        e.getErrors().getFirst().getMessage(),
                        location.getLineNr(),
                        location.getColumnNr()), e);
            }

            beanDefs.add(new BeanDefinition(
                    getBeanType(jsonNode),
                    "bean-" + idx++,
                    "default",
                    randomUUID().toString(),
                    jsonNode));
        }
    }

    /**
     * Entry point used by the runtime to consume a YAML stream and turn it into
     * a {@link BeanRegistry} that the router can work with.
     * <ul>
     *   <li>Reads the entire stream as UTF-8.</li>
     *   <li>Splits multi-document YAML ("---" separators).</li>
     *   <li>Validates each document against the JSON Schema provided by {@code grammar}.</li>
     *   <li>Emits helpful line/column locations for malformed multi-document input.</li>
     * </ul>
     * The returned registry is fully populated and {@link BeanRegistryImplementation#fireConfigurationLoaded()} has been called.
     * @param resource the input stream to parse. The method takes care of closing the stream.
     * @param grammar the grammar to use for type resolution and schema location
     * @return the bean registry
     */
    public static List<BeanDefinition> parseMembraneResources(@NotNull InputStream resource, Grammar grammar) throws IOException {
        try (resource) {
            return parseToBeanDefinitions(resource, grammar);
        } catch (JacksonException e) {
            throw new IOException(
                    "Invalid YAML: multiple configurations must be separated by '---' "
                            + "(at line " + e.getLocation().getLineNr()
                            + ", column " + e.getLocation().getColumnNr() + ").",
                    e
            );
        }
    }

    private static List<BeanDefinition> parseToBeanDefinitions(@NotNull InputStream resource, Grammar grammar) throws IOException {
        return new GenericYamlParser(grammar, new String(resource.readAllBytes(), UTF_8))
                .getBeanDefinitions();
    }

    public List<BeanDefinition> getBeanDefinitions() {
        return beanDefs;
    }

    private static String getBeanType(JsonNode jsonNode) {
        ensureSingleKey(jsonNode);
        return jsonNode.propertyNames().iterator().next();
    }

    private static void validate(Grammar grammar, JsonNode input) throws YamlSchemaValidationException {
        Schema schema = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, builder -> {}).getSchema(SchemaLocation.of(grammar.getSchemaLocation()));
        schema.initializeValidators();
        List<Error> errors = schema.validate(
                input.toString(),
                InputFormat.JSON,
                executionContext -> {}
        );
        if (!errors.isEmpty()) {
            throw new YamlSchemaValidationException("Invalid YAML.", errors);
        }
    }


    /**
     * Parse a top-level Membrane resource of the given {@code kind}.
     * <p>Ensures the node contains exactly one key (the kind), resolves the Java class via the
     * grammar and delegates to {@link #createAndPopulateNode(ParsingContext, Class, JsonNode)}.</p>
     */
    public static Object readMembraneObject(String kind, Grammar grammar, JsonNode node, BeanRegistry registry) throws ParsingException {
        ensureSingleKey(node);
        Class<?> clazz = grammar.getElement(kind);
        if (clazz == null)
            throw new ParsingException("Did not find java class for kind '%s'.".formatted(kind), node);
        return createAndPopulateNode(new ParsingContext(kind, registry, grammar), clazz, node.get(kind));
    }

    /**
     * Creates and populates an instance of {@code clazz} from the given YAML/JSON node.
     * - Arrays: only valid for {@code @MCElement(noEnvelope=true)}; items are parsed and passed to the single {@code @MCChildElement} list setter.
     * - Objects: each field is mapped to a setter resolved by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)};
     *   values are produced by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)}. A top-level {@code "$ref"} injects a previously defined bean.
     * All failures are wrapped in a {@link ParsingException} with location information.
     */
    public static <T> T createAndPopulateNode(ParsingContext ctx, Class<T> clazz, JsonNode node) throws ParsingException {
        try {
            T configObj = clazz.getConstructor().newInstance();
            if (node.isArray()) {
                // when this is a list, we are on a @MCElement(..., noEnvelope=true)

                Method method = getSingleChildSetter(clazz);
                method.invoke(configObj, (Object) parseListExcludingStartEvent(ctx, node));
                return configObj;
            }
            ensureMappingStart(node);
            if (isNoEnvelope(clazz))
                throw new RuntimeException("Class " + clazz.getName() + " is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.");

            for (String key : node.propertyNames()) {
                try {

                    if ("$ref".equals(key)) {
                        handleTopLevelRefs(clazz, node.get(key), ctx.registry(), configObj);
                        continue;
                    }

                    getMethodSetter(ctx, clazz, key).setSetter(configObj, ctx, node, key);
                } catch (Throwable cause) {
                    throw new ParsingException(cause, node.get(key));
                }
            }
            return configObj;
        } catch (Throwable cause) {
            throw new ParsingException(cause, node);
        }
    }

    private static <T> void handleTopLevelRefs(Class<T> clazz, JsonNode node, BeanRegistry registry, T obj) throws InvocationTargetException, IllegalAccessException {
        ensureTextual(node, "Expected a string after the '$ref' key.");
        Object o = registry.resolveReference(node.asString());
        getChildSetter(clazz, o.getClass()).invoke(obj, o);
    }

    public static List<Object> parseListIncludingStartEvent(ParsingContext context, JsonNode node) throws ParsingException {
        ensureArray(node);
        return parseListExcludingStartEvent(context, node);
    }

    private static @NotNull ArrayList<Object> parseListExcludingStartEvent(ParsingContext context, JsonNode node) throws ParsingException {
        ArrayList<Object> res = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            res.add(parseMapToObj(context, node.get(i)));
        }
        return res;
    }

    /**
     * Parses a single-item map node like { kind: {...} } by extracting the only key and
     * delegating to {@link #parseMapToObj(ParsingContext, JsonNode, String)}.
     */
    private static Object parseMapToObj(ParsingContext context, JsonNode node) throws ParsingException {
        ensureSingleKey(node);
        String key = node.propertyNames().iterator().next();
        return parseMapToObj(context, node.get(key), key);
    }

    private static Object parseMapToObj(ParsingContext ctx, JsonNode node, String key) throws ParsingException {
        if ("$ref".equals(key))
            return ctx.registry().resolveReference(node.asString());
        return createAndPopulateNode(ctx.updateContext(key), ctx.resolveClass(key), node);
    }
}