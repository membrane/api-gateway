/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import org.jetbrains.annotations.*;

import javax.lang.model.util.*;
import java.lang.reflect.*;
import java.util.*;

import static com.predic8.membrane.annot.yaml.GenericYamlParser.*;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.*;
import static com.predic8.membrane.annot.yaml.YamlParsingUtils.*;
import static java.lang.Boolean.*;
import static java.lang.Double.*;
import static java.lang.Integer.*;
import static java.lang.Long.*;
import static java.util.Locale.*;

public class MethodSetter {

    private final Method setter;
    private final Class<?> beanClass;

    public MethodSetter(Method setter, Class<?> beanClass) {
        this.setter = setter;
        this.beanClass = beanClass;
    }

    /**
     * Resolves which setter on {@code clazz} should handle the given YAML field {@code key} and,
     * if needed, which bean class that field represents.
     * Throws a {@link RuntimeException} if neither a matching setter nor a resolvable bean class can be found.
     * @param clazz Searches for a setter on this class.
     * @param key Searches for a setter with this name.
     */
    public static <T> @NotNull MethodSetter getMethodSetter(ParsingContext<?> ctx, Class<T> clazz, String key) {
        Method setter = findSetterForKey(clazz, key);
        // MCChildElements which are not lists are directly declared as beans,
        // their name should be interpreted as an element name
        if (setter != null && setter.getAnnotation(MCChildElement.class) != null) {
            if (!List.class.isAssignableFrom(setter.getParameterTypes()[0]))
                setter = null;
        }
        Class<?> beanClass = null;
        if (setter == null) {
            // if the element ONLY has a MCOtherAttributes and no MCAttributes and no MCChildElement setters, we avoid
            // global keyword resolution: the keyword will always be a key for MCOtherAttributes
            if (hasOtherAttributes(clazz) && !hasAttributes(clazz) && !hasChildren(clazz)) {
                return new MethodSetter(getAnySetter(clazz), null);
            }

            try {
                beanClass = ctx.grammar().getLocal(ctx.context(), key);
                if (beanClass == null)
                    beanClass = ctx.grammar().getElement(key);
                if (beanClass != null)
                    setter = getChildSetter(clazz, beanClass);
            } catch (Exception e) {
                throw new RuntimeException("Can't find method or bean for key '%s' in %s".formatted(key, getConfigElementName(clazz)), e);
            }
            if (setter == null)
                setter = getAnySetter(clazz);
            if (beanClass == null && setter == null) {
                var e = new ConfigurationParsingException("Can't find method or bean for key '%s' in %s".formatted(key, getConfigElementName(clazz)));
                e.setParsingContext(ctx.key(key));
                throw e;
            }
        }
        return new MethodSetter(setter, beanClass);
    }

    private static String getConfigElementName(Class<?> clazz) {
        MCChildElement childAnnotation = clazz.getAnnotation(MCChildElement.class);
        if (childAnnotation != null) {
            return childAnnotation.toString();
        }

        MCElement mcAnnotation = clazz.getAnnotation(MCElement.class);
        if (mcAnnotation != null) {
            return mcAnnotation.name();
        }

        return clazz.getSimpleName();
    }

    public Class<?> getParameterType() {
        return setter.getParameterTypes()[0];
    }

    public <T> void setSetter(T instance, ParsingContext<?> ctx, JsonNode node, String key) throws InvocationTargetException, IllegalAccessException, WrongEnumConstantException {
        setter.invoke(instance, resolveSetterValue(ctx, node.get(key), key));
    }

    private Object resolveSetterValue(ParsingContext<?> ctx, JsonNode node, String key) throws WrongEnumConstantException, ConfigurationParsingException {
        Class<?> wanted = getParameterType();

        // Collections / repeated elements
        List<Object> list = getObjectList(ctx, node, key, wanted);
        if (list != null) return list;

        // Structured objects
        if (McYamlIntrospector.isStructured(setter)) {
            if (beanClass != null) return createAndPopulateNode(ctx.updateContext(key).addPath("." + key), beanClass, node);
            return createAndPopulateNode(ctx.updateContext(key).addPath("." + key), wanted, node);
        }

        return coerceScalarOrReference(ctx, node, key, wanted);
    }

    /**
     * Attempts to coerce a given JSON node into the desired scalar, enum, reference, or map type,
     * as specified by the provided target class.
     *
     * @param ctx    The parsing context, providing access to type resolution and bean lookup mechanisms.
     * @param node   The JSON node to be coerced into the desired type.
     * @param key    The key corresponding to the JSON node, often used for error messages or map assignments.
     * @param wanted The target class specifying the type into which the node should be converted.
     * @return The coerced object, matching the desired type, derived from the input node.
     * @throws WrongEnumConstantException    If the node value does not match any of the constants in the enum type.
     * @throws ConfigurationParsingException If the provided type is unsupported for coercion or other unexpected issues arise.
     */
    Object coerceScalarOrReference(ParsingContext<?> ctx, JsonNode node, String key, Class<?> wanted) throws WrongEnumConstantException {
        assert node != null;

        if (wanted.equals(String.class)) {
            return node.isTextual() ? resolveSpelValue(node.asText(), String.class) : node.asText();
        }

        if (wanted.isEnum()) return parseEnum(wanted, node);

        if (node.isTextual()) {
            return coerceTextual(ctx, node, key, wanted);
        }

        return coerceNonTextual(ctx, node, key, wanted);
    }

    private Object coerceTextual(ParsingContext<?> pc, JsonNode node, String key, Class<?> wanted) {
        final String evaluated = evaluateSpelForString(node, key, node.asText());
        if (evaluated == null) {
            var e = new ConfigurationParsingException("SpEL for '%s' evaluated to null, but '%s' expects %s.".formatted(key, key, wanted.getSimpleName()));
            e.setParsingContext(pc);
            throw e;
        }
        final String value = evaluated.trim();

        if (isBoolean(wanted)) return parseBoolean(value);
        if (isNumber(wanted)) return parseNumericOrThrow(pc,key, wanted, evaluated, node);
        if (wanted == Map.class && hasOtherAttributes(setter)) return Map.of(key, evaluated);
        if (isBeanReference(wanted)) return resolveReference(pc, node, key, wanted);
        if (isReferenceAttribute(setter)) return pc.registry().resolve(evaluated);

        throw unsupported(wanted, key, node);
    }

    /**
     * Evaluates the given SpEL expression against the specified JSON node and returns the result as a string.
     */
    private static String evaluateSpelForString(JsonNode node, String key, String value) {
        try {
            return (String) resolveSpelValue(value, String.class);
        } catch (ConfigurationParsingException pe) {
            throw new ConfigurationParsingException("Invalid SpEL in '%s': %s".formatted(key, pe.getMessage()));
        }
    }

    /**
     * Parses a numeric string value into the desired numeric type and returns the parsed result.
     * If the input string cannot be converted to the specified type, a {@code ParsingException} is thrown.
     */
    private Object parseNumericOrThrow(ParsingContext pc, String key, Class<?> wanted, String value, JsonNode node) {
        try {
            if (isInteger(wanted)) return parseInt(value);
            if (isLong(wanted)) return parseLong(value);
            if (isDouble(wanted)) return parseDouble(value);
        } catch (NumberFormatException nfe) {
            var e = new ConfigurationParsingException("Invalid value for '%s': expected %s, but got '%s'. If you meant SpEL, use \"#{...}\" (e.g. \"#{env('PORT')}\").".formatted(key, wanted.getSimpleName(), value));
            e.setParsingContext(pc.key(key));
            throw e;
        }
        throw unsupported(wanted, key, node);
    }

    private Object coerceNonTextual(ParsingContext<?> ctx, JsonNode node, String key, Class<?> wanted) {
        if (isInteger(wanted)) return node.isInt() ? node.intValue() : parseInt(node.asText());
        if (isLong(wanted)) return node.isLong() || node.isInt() ? node.longValue() : parseLong(node.asText());
        if (isDouble(wanted)) return node.isNumber() ? node.doubleValue() : parseDouble(node.asText());
        if (isBoolean(wanted)) return node.isBoolean() ? node.booleanValue() : parseBoolean(node.asText());
        if (wanted.equals(Map.class) && hasOtherAttributes(setter)) return Map.of(key, node.asText());
        if (isReferenceAttribute(setter)) return ctx.registry().resolve(node.asText());
        throw unsupported(wanted, key, node);
    }

    private @Nullable List<Object> getObjectList(ParsingContext ctx, JsonNode node, String key, Class<?> wanted) {
        if (!Collection.class.isAssignableFrom(wanted))
            return null;

        Class<?> elemType = getCollectionElementType(setter);
        List<Object> list = parseListIncludingStartEvent(ctx.addPath("."+key), node, elemType);
        if (elemType != null) {
            for (Object o : list) {
                if (o == null) continue;
                if (!elemType.isAssignableFrom(o.getClass())) {
                    throw new ConfigurationParsingException("Value of type '%s' is not allowed in list '%s'. Expected '%s'."
                            .formatted(McYamlIntrospector.getElementName(o.getClass()), key, elemType.getSimpleName()));
                }
            }
        }
        return list;
    }

    private static @NotNull Object resolveReference(ParsingContext ctx, JsonNode node, String key, Class<?> wanted) {
        String ref = node.asText();
        final Object resolved;
        try {
            resolved = ctx.registry().resolve(ref);
        } catch (RuntimeException e) {
            throw new ConfigurationParsingException(e);
        }
        if (!wanted.isAssignableFrom(resolved.getClass())) {
            throw new ConfigurationParsingException(
                    "Referenced bean '%s' has type '%s' but '%s' expects '%s'."
                            .formatted(ref, resolved.getClass().getName(), key, wanted.getName())
            );
        }
        return resolved;
    }

    /**
     * Mirrors {@link com.predic8.membrane.annot.model.AttributeInfo#analyze(Types)}.
     */
    private boolean isBeanReference(Class<?> wanted) {
        if (wanted == Integer.TYPE || wanted == Long.TYPE || wanted == Float.TYPE || wanted == Double.TYPE || wanted == Boolean.TYPE || wanted == String.class)
            return false;
        return !wanted.isEnum();
    }

    public Method getSetter() {
        return setter;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    private static <E extends Enum<E>> E parseEnum(Class<?> enumClass, JsonNode node) throws WrongEnumConstantException {
        String value = node.asText().toUpperCase(ROOT);
        try {
            return Enum.valueOf((Class<E>) enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new WrongEnumConstantException(enumClass, value);
        }
    }

    static Class<?> getCollectionElementType(Method setter) {
        Type t = setter.getGenericParameterTypes()[0];
        if (!(t instanceof ParameterizedType pt)) return null;
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof Class<?> c) return c;
        if (arg instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length == 1 && upper[0] instanceof Class<?> uc) return uc;
        }
        if (arg instanceof ParameterizedType p2 && p2.getRawType() instanceof Class<?> rc) return rc;
        return null;
    }

    private static boolean isInteger(Class<?> wanted) {
        return wanted == int.class || wanted == Integer.class;
    }

    private static boolean isLong(Class<?> wanted) {
        return wanted == long.class || wanted == Long.class;
    }

    private static boolean isDouble(Class<?> wanted) {
        return wanted == double.class || wanted == Double.class;
    }

    private static boolean isBoolean(Class<?> wanted) {
        return wanted == boolean.class || wanted == Boolean.class;
    }

    private static boolean isNumber(Class<?> wanted) {
        return isInteger(wanted) || isLong(wanted) || isDouble(wanted);
    }

    private static ConfigurationParsingException unsupported(Class<?> wanted, String key, JsonNode node) {
        return new ConfigurationParsingException("Unsupported setter type: %s for key '%s' with node type %s".formatted(wanted.getName(), key, node.getNodeType()));
    }

}
