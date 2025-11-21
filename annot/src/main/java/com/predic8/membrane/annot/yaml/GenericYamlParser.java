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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.networknt.schema.Error;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.predic8.membrane.annot.K8sHelperGenerator;
import com.predic8.membrane.annot.MCChildElement;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.events.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION;
import static com.fasterxml.jackson.dataformat.yaml.YAMLFactory.builder;
import static com.networknt.schema.SpecificationVersion.DRAFT_2020_12;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

public class GenericYamlParser {
    private static final Logger log = LoggerFactory.getLogger(GenericYamlParser.class);
    private static final String EMPTY_DOCUMENT_WARNING = "Skipping empty document. Maybe there are two --- separators but no configuration in between.";

    private static final YAMLFactory yamlFactory = builder().enable(STRICT_DUPLICATE_DETECTION).build();
    private static final ObjectMapper om = new ObjectMapper(yamlFactory);


    /**
      * Parses Membrane resources from a YAML input stream.
      * @param resource the input stream to parse
      * @param generator the K8s helper generator
      * @param observer the bean cache observer
      * @return the bean registry
      */
    public static BeanRegistry parseMembraneResources(@NotNull InputStream resource, K8sHelperGenerator generator, BeanCacheObserver observer) throws IOException, InterruptedException {
        BeanCache registry = new BeanCache(observer, generator);
        registry.start();

        try (resource; YAMLParser parser = yamlFactory.createParser(new String(resource.readAllBytes(), UTF_8))) {
            int count = 0;

            while (!parser.isClosed()) {
                JsonNode node = om.readTree(parser);
                if (node == null || node.isNull()) {
                    log.debug(EMPTY_DOCUMENT_WARNING);
                    parser.nextToken();
                    continue;
                }
                validate(generator, node);

                Map<String, Object> m = om.convertValue(node, Map.class);
                if (m == null) {
                    log.debug(EMPTY_DOCUMENT_WARNING);
                    parser.nextToken();
                    continue;
                }
                count++;

                registry.handle(WatchAction.ADDED, new BeanDefinition(
                        m.keySet().stream().findFirst().get(), "bean-" + count, "default", UUID.randomUUID().toString(), m));
                parser.nextToken();
            }

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

    public static void validate(K8sHelperGenerator generator, JsonNode input) throws IOException {
        var jsonSchemaFactory = SchemaRegistry.withDefaultDialect(DRAFT_2020_12, builder -> {
        });
        var schema = jsonSchemaFactory.getSchema(SchemaLocation.of(generator.getSchemaLocation()));
        schema.initializeValidators();
        List<Error> errors = schema.validate(input);
        if (!errors.isEmpty())
            throw new RuntimeException("Invalid YAML: " + errors);
    }


    public static Object readMembraneObject(String kind, K8sHelperGenerator generator, String yaml, BeanRegistry registry) {

        Iterator<Event> events = new Yaml().parse(new StringReader(yaml)).iterator();
        Event event = events.next();
        if (!(event instanceof StreamStartEvent))
            throw new IllegalStateException("Expected StreamStartEvent in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));
        event = events.next();
        if (!(event instanceof DocumentStartEvent))
            throw new IllegalStateException("Expected DocumentStartEvent in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));
        event = events.next();
        ensureMappingStart(event);
        event = events.next();
        if (!(event instanceof ScalarEvent))
            throw new IllegalStateException("Expected scalar in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));

        Class clazz = generator.getElement(kind);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for kind '%s' in line %d column %d.".formatted(kind, event.getStartMark().getLine(), event.getStartMark().getColumn()));
        Object result = GenericYamlParser.parse(kind, clazz, events, registry, generator);

        event = events.next();
        if (!(event instanceof MappingEndEvent))
            throw new IllegalStateException("Expected MappingEndEvent in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));
        event = events.next();
        if (!(event instanceof DocumentEndEvent))
            throw new IllegalStateException("Expected DocumentEndEvent in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));
        event = events.next();
        if (!(event instanceof StreamEndEvent))
            throw new IllegalStateException("Expected StreamEndEvent in line %d column %d".formatted(event.getStartMark().getLine(), event.getStartMark().getColumn()));

        return result;
    }

    @SuppressWarnings({"rawtypes"})
    public static <T> T parse(String context, Class<T> clazz, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = null;
        Mark lastContextMark = null;
        try {
            T obj = clazz.getConstructor().newInstance();
            event = events.next();
            if (event instanceof SequenceStartEvent) {
                // when this is a list, we are on a @MCElement(..., noEnvelope=true)
                setSetter(obj, getSingleChildSetter(clazz), parseListExcludingStartEvent(context, events, registry, k8sHelperGenerator));
                return obj;
            }
            ensureMappingStart(event);
            if (isNoEnvelope(clazz))
                throw new RuntimeException("Class " + clazz.getName() + " is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.");

            while (true) {
                event = events.next();

                if (event instanceof MappingEndEvent) break;

                String key = getScalarKey(event);
                lastContextMark = event.getStartMark();

                if ("$ref".equals(key)) {
                    handleTopLevelRefs(clazz, events, registry, obj);
                    continue;
                }

                Method setter = getSetter(clazz, key);
                if (setter != null && setter.getAnnotation(MCChildElement.class) != null) {
                    if (!List.class.isAssignableFrom(setter.getParameterTypes()[0]))
                        setter = null;
                }
                Class clazz2 = null;
                if (setter == null) {
                    try {
                        clazz2 = k8sHelperGenerator.getLocal(context, key);
                        if (clazz2 == null)
                            clazz2 = k8sHelperGenerator.getElement(key);
                        if (clazz2 != null)
                            setter = getChildSetter(clazz, clazz2);
                    } catch (Exception e) {
                        throw new RuntimeException("Can't find method or bean for key: " + key + " in " + clazz.getName(), e);
                    }
                    if (setter == null)
                        setter = getAnySetter(clazz);
                    if (clazz2 == null && setter == null)
                        throw new RuntimeException("Can't find method or bean for key: " + key + " in " + clazz.getName());
                }

                setSetter(obj, setter, resolveSetterValue((Class) setter.getParameterTypes()[0], setter, context, events, registry, key, clazz2, event, k8sHelperGenerator));
            }
            return obj;
        } catch (Throwable cause) {
            Mark problemMark = event != null ? event.getStartMark() : null;
            // Fall back if we don't have marks
            if (problemMark == null && lastContextMark == null) {
                throw new RuntimeException("YAML parse error: " + cause.getMessage(), cause);
            }
            // This exception type prints a caret + snippet automatically
            throw new PublicMarkedYAMLException(
                    "while parsing " + clazz.getSimpleName(),
                    lastContextMark,
                    cause.getMessage(),
                    problemMark,
                    cause.getMessage()
            );
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object resolveSetterValue(Class<?> wanted, Method setter, String context, Iterator<Event> events, BeanRegistry registry, String key, Class<?> clazz2, Event event, K8sHelperGenerator k8sHelperGenerator) throws WrongEnumConstantException {
        if (wanted.equals(List.class) || wanted.equals(Collection.class)) {
            return parseListIncludingStartEvent(context, events, registry, k8sHelperGenerator);
        }
        if (wanted.isEnum()) {
            String value = YamlLoader.readString(events).toUpperCase(ROOT);
            try {
                return Enum.valueOf((Class<Enum>) wanted, value);
            } catch (IllegalArgumentException e) {
                throw new WrongEnumConstantException(wanted, value);
            }
        }
        if (wanted.equals(String.class)) {
            return YamlLoader.readString(events);
        }
        if (wanted.equals(Integer.TYPE)) {
            return Integer.parseInt(YamlLoader.readString(events));
        }
        if (wanted.equals(Long.TYPE)) {
            return Long.parseLong(YamlLoader.readString(events));
        }
        if (wanted.equals(Boolean.TYPE)) {
            return Boolean.parseBoolean(YamlLoader.readString(events));
        }
        if (wanted.equals(Map.class) && hasOtherAttributes(setter)) {
            return Map.of(key, YamlLoader.readString(events));
        }
        if (isStructured(setter)) {
            if (clazz2 != null) {
                return parseMapToObj(context, events, event, registry, k8sHelperGenerator);
            } else {
                return parse(context, wanted, events, registry, k8sHelperGenerator);
            }
        }
        if (isReferenceAttribute(setter)) {
            return registry.resolveReference(YamlLoader.readString(events));
        }
        throw new RuntimeException("Not implemented setter type " + wanted);
    }

    private static <T> void handleTopLevelRefs(Class<T> clazz, Iterator<Event> events, BeanRegistry registry, T obj) throws InvocationTargetException, IllegalAccessException {
        Event event = events.next();
        if (!(event instanceof ScalarEvent))
            throw new IllegalStateException("Expected a string after the '$ref' key.");
        Object o = registry.resolveReference(((ScalarEvent) event).getValue());
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

    private static List<?> parseListIncludingStartEvent(String context, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = events.next();
        if (!(event instanceof SequenceStartEvent)) {
            throw new IllegalStateException("Expected start-of-sequence in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        }
        return parseListExcludingStartEvent(context, events, registry, k8sHelperGenerator);
    }

    private static @NotNull ArrayList<?> parseListExcludingStartEvent(String context, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event;
        ArrayList res = new ArrayList();
        while (true) {
            event = events.next();
            if (event instanceof SequenceEndEvent)
                break;
            else if (!(event instanceof MappingStartEvent))
                throw new IllegalStateException("Expected end-of-sequence or begin-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
            res.add(parseMapToObj(context, events, registry, k8sHelperGenerator));
        }

        return res;
    }


    private static Object parseMapToObj(String context, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = events.next();
        if (!(event instanceof ScalarEvent))
            throw new IllegalStateException("Expected scalar in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        Object o = parseMapToObj(context, events, event, registry, k8sHelperGenerator);
        event = events.next();
        if (!(event instanceof MappingEndEvent))
            throw new IllegalStateException("Expected end-of-map or begin-of-map in line " + event.getStartMark().getLine() + " column " + event.getStartMark().getColumn());
        return o;
    }

    private static Object parseMapToObj(String context, Iterator<Event> events, Event event, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        String key = ((ScalarEvent) event).getValue();
        if ("$ref".equals(key)) {
            event = events.next();
            if (!(event instanceof ScalarEvent se))
                throw new IllegalStateException("Expected a string after the '$ref' key.");
            return registry.resolveReference(se.getValue());
        }
        return parse(key, getAClass(context, key, k8sHelperGenerator), events, registry, k8sHelperGenerator);
    }

    private static @NotNull Class<?> getAClass(String context, String key, K8sHelperGenerator k8sHelperGenerator) {
        Class<?> clazz = k8sHelperGenerator.getLocal(context, key);
        if (clazz == null)
            clazz = k8sHelperGenerator.getElement(key);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for key '" + key + "'.");
        return clazz;
    }

    private static <T> void setSetter(T instance, Method method, Object value)
            throws InvocationTargetException, IllegalAccessException {
        method.invoke(instance, value);
    }

}