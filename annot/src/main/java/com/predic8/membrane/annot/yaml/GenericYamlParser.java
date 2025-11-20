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

import com.predic8.membrane.annot.K8sHelperGenerator;
import org.jetbrains.annotations.NotNull;
import org.snakeyaml.engine.v2.events.*;
import org.snakeyaml.engine.v2.exceptions.Mark;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static java.util.Locale.ROOT;

public class GenericYamlParser {

    public static Object parseMembraneObject(Iterator<Event> events, K8sHelperGenerator generator, BeanRegistry registry) {
        int state = 0;
        while (events.hasNext()) {
            Event event = events.next();
            switch (state) {
                case 0:
                    if (event instanceof MappingStartEvent)
                        state = 1;
                    break;
                case 1:
                    if (event instanceof ScalarEvent se) {
                        String value = se.getValue();
                        return readMembraneObject(value, generator, events, registry);
                    } else if (event instanceof MappingEndEvent) {
                        throw new IllegalStateException("Not handled: MappingEndEvent"); // TODO: ?
                    } else {
                        throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
                    }
            }
        }
        return null;
    }

    private static Object readMembraneObject(String kind, K8sHelperGenerator generator, Iterator<Event> events, BeanRegistry registry) {
        Class clazz = generator.getElement(kind);
        if (clazz == null)
            throw new RuntimeException("Did not find java class for kind '%s'.".formatted(kind));
        return GenericYamlParser.parse(kind, clazz, events, registry, generator);
    }



    @SuppressWarnings({"rawtypes"})
    public static <T> T parse(String context, Class<T> clazz, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = null;
        Optional<Mark> lastContextMark = null;
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
            Optional<Mark> problemMark = event != null ? event.getStartMark() : null;
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
//            throw new RuntimeException(e);
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
            }
            catch (IllegalArgumentException e) {
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
            throw new IllegalStateException("Expected scalar or end-of-map in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
        }
        return ((ScalarEvent)event).getValue();
    }

    private static void ensureMappingStart(Event event) {
        if (!(event instanceof MappingStartEvent)) {
            throw new IllegalStateException("Expected start-of-map in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
        }
    }

    private static List<?> parseListIncludingStartEvent(String context, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = events.next();
        if (!(event instanceof SequenceStartEvent)) {
            throw new IllegalStateException("Expected start-of-sequence in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
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
                throw new IllegalStateException("Expected end-of-sequence or begin-of-map in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
            res.add(parseMapToObj(context, events, registry, k8sHelperGenerator));
        }

        return res;
    }


    private static Object parseMapToObj(String context, Iterator<Event> events, BeanRegistry registry, K8sHelperGenerator k8sHelperGenerator) {
        Event event = events.next();
        if (!(event instanceof ScalarEvent))
            throw new IllegalStateException("Expected scalar in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
        Object o = parseMapToObj(context, events, event, registry, k8sHelperGenerator);
        event = events.next();
        if (!(event instanceof MappingEndEvent))
            throw new IllegalStateException("Expected end-of-map or begin-of-map in line " + event.getStartMark().orElseThrow().getLine() + " column " + event.getStartMark().orElseThrow().getColumn());
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