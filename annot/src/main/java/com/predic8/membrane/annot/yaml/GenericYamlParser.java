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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.predic8.membrane.annot.Grammar;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.annot.beanregistry.BeanDefinition;
import com.predic8.membrane.annot.beanregistry.BeanLifecycleManager;
import com.predic8.membrane.annot.beanregistry.BeanRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.MethodSetter.getCollectionElementType;
import static com.predic8.membrane.annot.yaml.MethodSetter.getMethodSetter;
import static com.predic8.membrane.annot.yaml.NodeValidationUtils.*;
import static com.predic8.membrane.annot.yaml.YamlParsingUtils.*;
import static java.lang.reflect.Modifier.isAbstract;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.List.of;
import static java.util.Locale.ROOT;
import static java.util.UUID.randomUUID;

public class GenericYamlParser {
    private static final Logger log = LoggerFactory.getLogger(GenericYamlParser.class);
    private static final String EMPTY_DOCUMENT_WARNING = "Skipping empty document. Maybe there are two --- separators but no configuration in between.";

    private final List<BeanDefinition> beanDefs = new ArrayList<>();

    private static final ObjectMapper SCALAR_MAPPER = new ObjectMapper();

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

        var idx = 0;
        for (JsonNode jsonNode : jsonLocationMap.parseWithLocations(yaml)) {
            if (jsonNode == null || jsonNode.isNull() || jsonNode.isEmpty()) {
                log.debug(GenericYamlParser.EMPTY_DOCUMENT_WARNING);
                continue;
            }

            // Deactivated temporarily to get better error messages
            //validateAgainstSchema(grammar, jsonNode, jsonLocationMap);

            var pc = new ParsingContext<>("",null,grammar, jsonNode, "$",null);

            if ("components".equals(getBeanType(pc, jsonNode))) {
                beanDefs.addAll(extractComponentBeanDefinitions(pc.addPath(".components"),jsonNode.get("components")));
            }

            beanDefs.add(new BeanDefinition(
                    getBeanType(pc, jsonNode),
                    "bean-" + idx++,
                    "default",
                    randomUUID().toString(),
                    jsonNode));
        }
    }

    private static void validateAgainstSchema(Grammar grammar, JsonNode jsonNode, JsonLocationMap jsonLocationMap) throws IOException {
        // Validate YAML against JSON schema
        try {
            validate(grammar, jsonNode);
        } catch (YamlSchemaValidationException e) {
            JsonLocation location = jsonLocationMap.getLocationMap().get(
                    e.getErrors().getFirst().getInstanceNode());
            throw new IOException("Invalid YAML: %s at line %d, column %d.".formatted(
                    e.getErrors().getFirst().getMessage(),
                    location.getLineNr(),
                    location.getColumnNr()), e);
        }
    }

    /**
     * Entry point used by the runtime to consume a YAML stream.
     * <ul>
     *   <li>Reads the entire stream as UTF-8.</li>
     *   <li>Splits multi-document YAML ("---" separators).</li>
     *   <li>Validates each document against the JSON Schema provided by {@code grammar}.</li>
     *   <li>Emits helpful line/column locations for malformed multi-document input.</li>
     * </ul>
     * @param resource the input stream to parse. The method takes care of closing the stream.
     * @param grammar the grammar to use for type resolution and schema location
     * @return list of parsed bean definitions
     */
    public static List<BeanDefinition> parseMembraneResources(@NotNull InputStream resource, Grammar grammar) throws IOException {
        try (resource) {
            return parseToBeanDefinitions(resource, grammar);
        } catch (JsonParseException e) {
            throw new IOException("Invalid YAML: multiple configurations must be separated by '---' (at line %d, column %d).".formatted(e.getLocation().getLineNr(), e.getLocation().getColumnNr()), e);
        }
    }

    private static List<BeanDefinition> parseToBeanDefinitions(@NotNull InputStream resource, Grammar grammar) throws IOException {
        return new GenericYamlParser(grammar, new String(resource.readAllBytes(), UTF_8))
                .getBeanDefinitions();
    }

    public List<BeanDefinition> getBeanDefinitions() {
        return beanDefs;
    }

    private static String getBeanType(ParsingContext<?> ctx, JsonNode jsonNode) {
        ensureSingleKey(ctx, jsonNode);
        return jsonNode.fieldNames().next();
    }

    /**
     * Parse a top-level Membrane resource of the given {@code kind}.
     * <p>Ensures the node contains exactly one key (the kind), resolves the Java class via the
     * grammar and delegates to {@link #createAndPopulateNode(ParsingContext, Class, JsonNode)}.</p>
     */
    public static <R extends BeanRegistry & BeanLifecycleManager> Object readMembraneObject(String kind, Grammar grammar, JsonNode node, R registry) throws ConfigurationParsingException {
        return createAndPopulateNode(new ParsingContext<>(kind, registry, grammar,node, "$." + kind,null), decideClazz(kind, grammar, node), node.get(kind));
    }

    /**
     * Detects the class that will be selected to represent the node in Java.
     */
    public static Class<?> decideClazz(String kind, Grammar grammar, JsonNode node) {
        ensureSingleKey(new ParsingContext("",null, grammar,node,"$",null),node);
        Class<?> clazz = grammar.getElement(kind);
        if (clazz == null) {
            var pc = new ParsingContext("", null,grammar,node,"$",null).key(kind);
            throw new ConfigurationParsingException("Did not find java class for kind '%s'.".formatted(kind),null,pc);
        }
        return clazz;
    }

    /**
     * Creates and populates an instance of {@code clazz} from the given YAML/JSON node.
     * - Arrays: only valid for {@code @MCElement(noEnvelope=true)}; items are parsed and passed to the single {@code @MCChildElement} list setter.
     * - Objects: each field is mapped to a setter resolved by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)};
     *   values are produced by {@link MethodSetter#getMethodSetter(ParsingContext, Class, String)}. A top-level {@code "$ref"} injects a previously defined bean.
     * All failures are wrapped in a {@link ConfigurationParsingException} with location information.
     */
    public static <T> T createAndPopulateNode(ParsingContext<?> pc, Class<T> clazz, JsonNode node) throws ConfigurationParsingException {
        try {
            T configObj = clazz.getConstructor().newInstance();

            // when this is a list, we are on a @MCElement(..., noEnvelope=true)
            if (node.isArray()) {
                return handlePostConstructAndPreDestroy(pc, handleNoEnvelopeList(pc, clazz, node, configObj));
            }

            // scalar inline form for @MCElement(collapsed=true)
            if (isCollapsed(clazz)) {
                return handleCollapsed(pc, clazz, node, configObj);
            }
            ensureMappingStart(node);
            if (isNoEnvelope(clazz)) {
                log.error("Class {} is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.", clazz.getName());
                throw new ConfigurationParsingException("Class %s is annotated with @MCElement(noEnvelope=true), but the YAML/JSON structure does not contain a list.".formatted(clazz.getName()),null,pc);
            }

            JsonNode refNode = node.get("$ref");
            if (refNode != null) {
                applyObjectLevelRef(pc, clazz, node, refNode, configObj);
            }

            List<Method> required = findRequiredSetters(clazz);
            populateObjectFields(pc, clazz, node, required, configObj);

            if (!required.isEmpty())
                throw new ConfigurationParsingException("Missing required fields: " + required.stream().map(McYamlIntrospector::getSetterName).toList());
            return handlePostConstructAndPreDestroy(pc, configObj);
        } catch (NoClassDefFoundError e) {
            if (e.getCause() != null) {
                var missingClass = e.getCause().getMessage(); // TODO: Better use ExceptionUtil.getRootCause() but it isn't visible in annot.
                var msg = "Could not create bean with class: %s\nMissing class: %s\n".formatted(clazz, missingClass);
                log.error(msg);
                throw new ConfigurationParsingException(msg); // TODO: Cause we know the reason, shorten output.
            }
            throw new ConfigurationParsingException(e);
        } catch (ConfigurationParsingException e) {
            if (e.getParsingContext() == null)
                e.setParsingContext(pc);
            throw e;
        }
        catch (Throwable cause) {
            log.debug("",cause);
            throw new ConfigurationParsingException(cause);
        }
    }

    private static <T> void populateObjectFields(ParsingContext<?> ctx, Class<T> clazz, JsonNode node, List<Method> required, T configObj) {
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if ("$ref".equals(key))
                continue;

            try {
                var setter = getMethodSetter(ctx, clazz, key);
                required.remove(setter.getSetter());
                setter.setSetter(configObj, ctx, node, key);
            } catch (ConfigurationParsingException e) {
                throw e;
            }
            catch (Throwable cause) {
                log.debug("",cause);
                var e = new ConfigurationParsingException(cause.getMessage());
                e.setParsingContext(ctx.key(key));
                throw e;
            }
        }
    }

    private static <T> @NotNull T handleCollapsed(ParsingContext<?> ctx, Class<T> clazz, JsonNode node, T configObj) {
        if (node.isNull()) throw new ConfigurationParsingException("Collapsed element must not be null.");
        if (node.isArray() || node.isObject()) throw new ConfigurationParsingException("Element is collapsed; expected an inline scalar value, not an %s.".formatted((node.isArray() ? "array" : "object")));
        applyCollapsedScalar(clazz, node, configObj);
        return handlePostConstructAndPreDestroy(ctx, configObj);
    }

    private static <T> T handleNoEnvelopeList(ParsingContext<?> pc, Class<T> clazz, JsonNode node, T configObj) throws IllegalAccessException, InvocationTargetException {
        var childSetter = getSingleChildSetter(pc, clazz);
        childSetter.invoke(configObj, parseListExcludingStartEvent(pc, node, getCollectionElementType(childSetter)));
        return configObj;
    }

    private static List<BeanDefinition> extractComponentBeanDefinitions(ParsingContext<?> pc, JsonNode componentsNode) {
        if (componentsNode == null || componentsNode.isNull())
            return of();

        if (!componentsNode.isObject())
            throw new ConfigurationParsingException("Expected object for 'components'.");

        List<BeanDefinition> res = new ArrayList<>();

        Iterator<String> ids = componentsNode.fieldNames();
        while (ids.hasNext()) {
            String id = ids.next();
            JsonNode def = componentsNode.get(id);

            // Each component definition must have exactly one key (the component type)
            ensureSingleKey(pc.addPath("."+id),def);
            String componentKind = def.fieldNames().next();

            // Wrap it into a normal top-level node: { <kind>: <body> }
            ObjectNode wrapped = JsonNodeFactory.instance.objectNode();
            wrapped.set(componentKind, def.get(componentKind));

            res.add(new BeanDefinition(
                    componentKind,
                    "#/components/" + id,
                    "default",
                    randomUUID().toString(),
                    wrapped
            ));
        }
        return res;
    }

    /**
     * Applies an object-level "$ref" by resolving the referenced component and injecting it
     * into the parent object via the matching @MCChildElement setter.
     * Rejects "$ref" if the same child is already configured inline.
     */
    private static <T> void applyObjectLevelRef(ParsingContext<?> ctx, Class<T> parentClass, JsonNode parentNode, JsonNode refNode, T obj) throws ConfigurationParsingException {
        ensureTextual(refNode, "Expected a string after the '$ref' key.");
        Object referenced = getReferenced(ctx, refNode);
        String refKey = getElementName(referenced.getClass());

        // Forbid inline + $ref for the same child
        if (parentNode.has(refKey)) {
            throw new ConfigurationParsingException("Cannot use '$ref' together with inline '%s' in '%s'."
                    .formatted(refKey, ctx.getContext()));
        }

        try {
            getChildSetter(parentClass, referenced.getClass()).invoke(obj, referenced);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException(
                    "Referenced component '%s' (type '%s') is not allowed in '%s'."
                            .formatted(refNode.asText(), refKey, ctx.getContext()));
        } catch (Throwable t) {
            throw new ConfigurationParsingException(t);
        }
    }

    private static Object getReferenced(ParsingContext<?> ctx, JsonNode refNode) {
        try {
            return ctx.getRegistry().resolve(refNode.asText());
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException(e);
        }
    }

    public static List<Object> parseListIncludingStartEvent(ParsingContext<?> context, JsonNode node) throws ConfigurationParsingException {
        return parseListIncludingStartEvent(context, node, null);
    }

    public static List<Object> parseListIncludingStartEvent(ParsingContext<?> pc, JsonNode node, Class<?> elemType) throws ConfigurationParsingException {
        ensureArray(pc,node);
        return parseListExcludingStartEvent(pc, node, elemType);
    }

    private static @NotNull List<Object> parseListExcludingStartEvent(ParsingContext<?> pc, JsonNode node, Class<?> elemType) throws ConfigurationParsingException {
        List<Object> res = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            res.add(parseListItem(pc.addPath("[%d]".formatted(i)), node.get(i), elemType));
        }
        return res;
    }

    /**
     * Parses a single-item map node like { kind: {...} } by extracting the only key and
     * delegating to {@link #parseMapToObj(ParsingContext, JsonNode, String)}.
     */
    private static Object parseMapToObj(ParsingContext<?> pc, JsonNode node) throws ConfigurationParsingException {
        ensureSingleKey(pc,node);
        String key = node.fieldNames().next();
        return parseMapToObj(pc, node.get(key), key);
    }

    private static Object parseMapToObj(ParsingContext<?> ctx, JsonNode node, String key) throws ConfigurationParsingException {
        if ("$ref".equals(key))
            return ctx.getRegistry().resolve(node.asText());
        var c = ctx.addPath("." + key); // Check!
        return createAndPopulateNode(c.updateContext(key), c.resolveClass(key), node);
    }

    private static <T> void applyCollapsedScalar(Class<T> clazz, JsonNode node, T target) {
        if (node == null || node.isNull()) {
            throw new ConfigurationParsingException("Collapsed element must not be null.");
        }

        // Collapsed classes can only have one matching setter (ensured by SpringConfigurationXSDGeneratingAnnotationProcessor)
        Method attributeSetter = findSingleSetterOrNullForAnnotation(clazz, MCAttribute.class);
        Method textSetter = findSingleSetterOrNullForAnnotation(clazz, MCTextContent.class);

        Method setter = (attributeSetter != null) ? attributeSetter : textSetter;
        Class<?> paramType = Objects.requireNonNull(setter).getParameterTypes()[0];

        Object value;
        try {
            value = convertScalarOrSpel(node, paramType);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationParsingException("Cannot convert inline value to %s.".formatted(paramType.getSimpleName()));
        }

        try {
            setter.setAccessible(true);
            setter.invoke(target, value);
        } catch (InvocationTargetException e) {
            throw new ConfigurationParsingException(e.getTargetException());
        } catch (Throwable t) {
            throw new ConfigurationParsingException(t);
        }
    }

    static Object convertScalarOrSpel(JsonNode node, Class<?> targetType) {
        if (node == null || !node.isTextual()) return SCALAR_MAPPER.convertValue(node, targetType);
        return resolveSpelValue(node.asText(), targetType);
    }

    private static Object parseListItem(ParsingContext<?> ctx, JsonNode item, Class<?> elemType) throws ConfigurationParsingException {
        if (item == null || item.isNull()) throw new ConfigurationParsingException("List items must not be null.");

        // Non-object items (scalar/array): only supported for typed element lists (e.g. collapsed items).
        if (!item.isObject()) {
            return parseInlineListItem(ctx, item, elemType);
        }

        // $ref-only object is allowed, but mixing $ref with other fields is not.
        JsonNode ref = item.get("$ref");
        if (ref != null) {
            if (item.size() == 1) return parseMapToObj(ctx, item);
            throw new ConfigurationParsingException("Cannot mix '$ref' with other fields in a list item.");
        }

        // Single-key object: treat as inline if it matches a setter of the element type, otherwise wrapper form.
        if (item.size() == 1) {
            if (elemType != null && findSetterForKey(elemType, item.fieldNames().next()) != null) { // TODO always true indSetterForKey(elemType, item.fieldNames().next()) != null
                return parseInlineListItem(ctx, item, elemType);
            }
            return parseMapToObj(ctx, item);
        }

        return parseInlineListItem(ctx, item, elemType);
    }

    private static Object parseInlineListItem(ParsingContext<?> ctx, JsonNode node, Class<?> elemType) {
        if (elemType == null) throw new ConfigurationParsingException("Inline list item form requires a typed list element.");
        if (isScalarElementType(elemType)) {
            if (node.isObject() || node.isArray()) {
                throw new ConfigurationParsingException(
                        "Scalar list item expected for list of %s, but got %s."
                                .formatted(elemType.getSimpleName(), node.getNodeType()));
            }
            return coerceScalarListItem(node, elemType);
        }
        if (elemType.isInterface() || isAbstract(elemType.getModifiers()))
            throw new ConfigurationParsingException("Inline list item form requires a concrete element type, but found: %s.".formatted(elemType.getName()));
        return createAndPopulateNode(ctx.updateContext(getElementName(elemType)), elemType, node);
    }

    private static boolean isScalarElementType(Class<?> t) {
        return t == String.class
                || t == Boolean.class
                || t == Character.class
                || Number.class.isAssignableFrom(t)
                || t.isEnum();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerceScalarListItem(JsonNode node, Class<?> elemType) {
        if (elemType == String.class)
            return node.isTextual() ? resolveSpelValue(node.asText(), String.class) : node.asText();
        if (elemType.isEnum()) {
            String raw = node.asText();
            try {
                return Enum.valueOf((Class<? extends Enum>) elemType, raw);
            } catch (IllegalArgumentException ignored) {}
            try {
                return Enum.valueOf((Class<? extends Enum>) elemType, raw.toUpperCase(ROOT));
            } catch (IllegalArgumentException e) {
                throw new ConfigurationParsingException(
                        "Invalid value '%s' for enum type %s.".formatted(raw, elemType.getSimpleName()));
            }
        }
        return convertScalarOrSpel(node, elemType);
    }
}