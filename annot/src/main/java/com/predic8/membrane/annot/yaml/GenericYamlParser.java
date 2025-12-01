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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.annot.K8sHelperGenerator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.MethodSetter.getMethodSetter;
import static com.predic8.membrane.annot.yaml.MethodSetter.setSetter;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

public class GenericYamlParser {
    private static final Logger log = LoggerFactory.getLogger(GenericYamlParser.class);
    private static final String EMPTY_DOCUMENT_WARNING = "Skipping empty document. Maybe there are two --- separators but no configuration in between.";

    /**
      * Parses Membrane resources from a YAML input stream.
      * @param resource the input stream to parse. The method takes care of closing the stream.
      * @param generator the K8s helper generator
      * @param observer the bean cache observer
      * @return the bean registry
      */
    public static BeanRegistry parseMembraneResources(@NotNull InputStream resource, K8sHelperGenerator generator, BeanCacheObserver observer) throws IOException, YamlSchemaValidationException {
        BeanCache registry;
        try (resource) {
            registry = new BeanCache(observer, generator);
            registry.start();

            new GenericYamlParser(generator, new String(resource.readAllBytes(), UTF_8))
                    .getBeanDefinitions()
                    .forEach(bd -> {
                        registry.handle(WatchAction.ADDED, bd);
                    });

            registry.fireConfigurationLoaded();
        } catch (JsonParseException e) {
            throw new IOException(
                    "Invalid YAML: multiple configurations must be separated by '---' "
                            + "(at line " + e.getLocation().getLineNr()
                            + ", column " + e.getLocation().getColumnNr() + ").",
                    e
            );
        }

        return registry;
    }

    List<BeanDefinition> beanDefs = new ArrayList<>();

    public GenericYamlParser(K8sHelperGenerator generator, String yaml) throws IOException {
        JsonLocationMap jsonLocationMap = new JsonLocationMap();
        List<JsonNode> rootNodes = jsonLocationMap.parseWithLocations(yaml);
        for (int i = 0; i < rootNodes.size(); i++) {
            if (rootNodes.get(i) == null) {
                log.debug(GenericYamlParser.EMPTY_DOCUMENT_WARNING);
                rootNodes.remove(i);
                i--;
                continue;
            }
            try {
                validate(generator, rootNodes.get(i));
            } catch (YamlSchemaValidationException e) {
                JsonLocation location = jsonLocationMap.getLocationMap().get(
                        e.getErrors().getFirst().getInstanceNode());
                throw new IOException("Invalid YAML: %s at line %d, column %d.".formatted(
                        e.getErrors().getFirst().getMessage(),
                        location.getLineNr(),
                        location.getColumnNr()), e);
            }

            beanDefs.add(new BeanDefinition(
                    getBeanType(rootNodes.get(i)),
                    "bean-" + i,
                    "default",
                    UUID.randomUUID().toString(),
                    rootNodes.get(i)));
        }
    }

    public List<BeanDefinition> getBeanDefinitions() {
        return beanDefs;
    }

    private static String getBeanType(JsonNode jsonNode) {
        if (!jsonNode.isObject())
            throw new IllegalArgumentException("Expected object node.");
        if (jsonNode.size() != 1)
            throw new IllegalArgumentException("Expected exactly one key.");
        return jsonNode.fieldNames().next();
    }

    public static void validate(K8sHelperGenerator generator, JsonNode input) throws IOException, YamlSchemaValidationException {
        var jsonSchemaFactory = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, builder -> {
        });
        var schema = jsonSchemaFactory.getSchema(SchemaLocation.of(generator.getSchemaLocation()));
        schema.initializeValidators();
        List<Error> errors = schema.validate(input);
        if (!errors.isEmpty()) {
            throw new YamlSchemaValidationException("Invalid YAML.", errors);
        }
    }


    public static Object readMembraneObject(String kind, K8sHelperGenerator generator, JsonNode node, BeanRegistry registry) throws ParsingException {

        ensureMappingStart(node);

        if (node.size() > 1)
            throw new ParsingException("Expected exactly one key.", node);

        Class clazz = generator.getElement(kind);
        if (clazz == null)
            throw new ParsingException("Did not find java class for kind '%s'.".formatted(kind), node);
        ParsingContext parsingContext = new ParsingContext(kind, registry, generator);
        Object result = GenericYamlParser.parse(parsingContext, clazz, node.get(kind));

        return result;
    }

    @SuppressWarnings({"rawtypes"})
    public static <T> T parse(ParsingContext ctx, Class<T> clazz, JsonNode node) throws ParsingException {
        try {
            T obj = clazz.getConstructor().newInstance();
            if (node.isArray()) {
                // when this is a list, we are on a @MCElement(..., noEnvelope=true)
                setSetter(obj, getSingleChildSetter(clazz), parseListExcludingStartEvent(ctx, node));
                return obj;
            }
            ensureMappingStart(node);
            if (isNoEnvelope(clazz))
                throw new RuntimeException("Class " + clazz.getName() + " is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.");

            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                try {

                    if ("$ref".equals(key)) {
                        handleTopLevelRefs(clazz, node.get(key), ctx.registry(), obj);
                        continue;
                    }

                    MethodSetter setter = getMethodSetter(ctx, clazz, key);

                    setSetter(obj, setter.getSetter(), resolveSetterValue(setter, ctx, node.get(key), key));
                } catch (Throwable cause) {
                    throw new ParsingException(cause, node.get(key));
                }
            }
            return obj;
        } catch (Throwable cause) {
            throw new ParsingException(cause, node);
        }
    }

    private static Object resolveSetterValue(MethodSetter setter, ParsingContext ctx, JsonNode node, String key) throws WrongEnumConstantException, ParsingException {

        Class<?> clazz2 = setter.getBeanClass();
        Class<?> wanted = setter.getSetter().getParameterTypes()[0];
        if (wanted.equals(List.class) || wanted.equals(Collection.class)) {
            return parseListIncludingStartEvent(ctx, node);
        }
        if (wanted.isEnum()) {
            String value = readString(node).toUpperCase(ROOT);
            try {
                return Enum.valueOf((Class<Enum>) wanted, value);
            } catch (IllegalArgumentException e) {
                throw new WrongEnumConstantException(wanted, value);
            }
        }
        if (wanted.equals(String.class)) {
            return readString(node);
        }
        if (wanted.equals(Integer.TYPE)) {
            return Integer.parseInt(readString(node));
        }
        if (wanted.equals(Long.TYPE)) {
            return Long.parseLong(readString(node));
        }
        if (wanted.equals(Boolean.TYPE)) {
            return Boolean.parseBoolean(readString(node));
        }
        if (wanted.equals(Map.class) && hasOtherAttributes(setter.getSetter())) {
            return Map.of(key, readString(node));
        }
        if (isStructured(setter.getSetter())) {
            if (clazz2 != null) {
                return parse( ctx, clazz2, node);
            } else {
                return parse(ctx, wanted, node);
            }
        }
        if (isReferenceAttribute(setter.getSetter())) {
            return ctx.registry().resolveReference(readString(node));
        }
        throw new RuntimeException("Not implemented setter type " + wanted);
    }

    private static <T> void handleTopLevelRefs(Class<T> clazz, JsonNode node, BeanRegistry registry, T obj) throws InvocationTargetException, IllegalAccessException {
        if (!node.isTextual())
            throw new IllegalStateException("Expected a string after the '$ref' key.");
        Object o = registry.resolveReference(node.asText());
        setSetter(obj, getChildSetter(clazz, o.getClass()), o);
    }

    private static String getScalarKey(Event event) {
        if (!(event instanceof ScalarEvent)) {
            throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        }
        return ((ScalarEvent) event).getValue();
    }

    private static void ensureMappingStart(Event event) {
        if (!(event instanceof MappingStartEvent)) {
            throw new IllegalStateException("Expected start-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        }
    }

    private static void ensureMappingStart(JsonNode node) throws ParsingException {
        if (!(node.isObject())) {
            throw new ParsingException("Expected object", node);
        }
    }

    private static String readString(JsonNode node) {
        return node.asText();
    }

    private static List<?> parseListIncludingStartEvent(ParsingContext context, JsonNode node) throws ParsingException {
        if (!node.isArray()) {
            throw new ParsingException("Expected list.", node);
        }
        return parseListExcludingStartEvent(context, node);
    }

    private static @NotNull ArrayList<?> parseListExcludingStartEvent(ParsingContext context, JsonNode node) throws ParsingException {
        ArrayList res = new ArrayList();
        for (int i = 0; i < node.size(); i++) {
            res.add(parseMapToObj(context, node.get(i)));
        }
        return res;
    }

    private static Object parseMapToObj(ParsingContext context, JsonNode node) throws ParsingException {
        if (!node.isObject())
            throw new ParsingException("Expected object.", node);
        if (node.size() != 1)
            throw new ParsingException("Expected exactly one key.", node);
        String key = node.fieldNames().next();
        return parseMapToObj(context, node.get(key), key);
    }

    private static Object parseMapToObj(ParsingContext ctx, JsonNode node, String key) throws ParsingException {
        if ("$ref".equals(key)) {
            return ctx.registry().resolveReference(readString(node));
        }
        return parse(ctx.updateContext(key), getAClass(ctx, key), node);
    }

    private static @NotNull Class<?> getAClass(ParsingContext ctx, String key) {
        Class<?> clazz = ctx.k8sHelperGenerator().getLocal(ctx.context(), key);
        if (clazz == null)
            clazz = ctx.k8sHelperGenerator().getElement(key);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for key '" + key + "'.");
        return clazz;
    }
}