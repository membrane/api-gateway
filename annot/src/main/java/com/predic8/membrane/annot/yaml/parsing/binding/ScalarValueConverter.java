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

package com.predic8.membrane.annot.yaml.parsing.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.yaml.ConfigurationParsingException;
import com.predic8.membrane.annot.yaml.ParsingContext;
import com.predic8.membrane.annot.yaml.WrongEnumConstantException;
import com.predic8.membrane.annot.yaml.parsing.support.SpelEvaluator;

import java.lang.reflect.Method;
import java.util.Map;

import static com.predic8.membrane.annot.yaml.McYamlIntrospector.hasOtherAttributes;
import static com.predic8.membrane.annot.yaml.McYamlIntrospector.isReferenceAttribute;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.util.Locale.ROOT;

public final class ScalarValueConverter {

    private static final ObjectMapper SCALAR_MAPPER = new ObjectMapper();
    private static final SpelEvaluator STATIC_SPEL_EVALUATOR = new SpelEvaluator();

    private final SpelEvaluator spelEvaluator = new SpelEvaluator();
    private final ReferenceResolver referenceResolver = new ReferenceResolver();

    public Object coerceScalarOrReference(ParsingContext<?> ctx, Method setter, JsonNode node, String key, Class<?> wanted) throws WrongEnumConstantException {
        if (wanted.equals(String.class))
            return node.isTextual() ? spelEvaluator.resolve(node.asText(), String.class) : node.asText();

        if (wanted.isEnum())
            return parseEnum(wanted, node);

        if (node.isTextual())
            return coerceTextual(ctx, setter, node, key, wanted);

        return coerceNonTextual(ctx, setter, node, key, wanted);
    }

    public static Object convertScalarOrSpel(JsonNode node, Class<?> targetType) {
        if (node == null || !node.isTextual())
            return SCALAR_MAPPER.convertValue(node, targetType);
        return STATIC_SPEL_EVALUATOR.resolve(node.asText(), targetType);
    }

    private Object coerceTextual(ParsingContext<?> ctx, Method setter, JsonNode node, String key, Class<?> wanted) {
        String evaluated = evaluateSpelForString(key, node.asText());
        if (evaluated == null) {
            var e = new ConfigurationParsingException("SpEL for '%s' evaluated to null, but '%s' expects %s.".formatted(key, key, wanted.getSimpleName()));
            e.setParsingContext(ctx);
            throw e;
        }
        String value = evaluated.trim();

        if (isBoolean(wanted))
            return parseBoolean(value);
        if (isNumber(wanted))
            return parseNumericOrThrow(ctx, key, wanted, evaluated, node);
        if (wanted == Map.class && setter != null && hasOtherAttributes(setter))
            return Map.of(key, evaluated);
        if (isBeanReference(wanted))
            return referenceResolver.resolveReference(ctx, value, key, wanted);
        if (setter != null && isReferenceAttribute(setter))
            return resolveRegistryReference(ctx, value, key);

        throw unsupported(wanted, key, node);
    }

    private Object coerceNonTextual(ParsingContext<?> ctx, Method setter, JsonNode node, String key, Class<?> wanted) {
        if (isInteger(wanted))
            return node.isInt() ? node.intValue() : parseInt(node.asText());
        if (isLong(wanted))
            return node.isLong() || node.isInt() ? node.longValue() : parseLong(node.asText());
        if (isDouble(wanted))
            return node.isNumber() ? node.doubleValue() : parseDouble(node.asText());
        if (isBoolean(wanted))
            return node.isBoolean() ? node.booleanValue() : parseBoolean(node.asText());
        if (wanted.equals(Map.class) && setter != null && hasOtherAttributes(setter))
            return Map.of(key, node.asText());
        if (setter != null && isReferenceAttribute(setter))
            return resolveRegistryReference(ctx, node.asText(), key);
        throw unsupported(wanted, key, node);
    }

    private Object resolveRegistryReference(ParsingContext<?> ctx, String ref, String key) {
        if (ctx == null || ctx.getRegistry() == null)
            throw new ConfigurationParsingException("Cannot resolve reference: " + ref, null, ctx == null ? null : ctx.key(key));
        return ctx.getRegistry().resolve(ref);
    }

    private String evaluateSpelForString(String key, String value) {
        try {
            return (String) spelEvaluator.resolve(value, String.class);
        } catch (ConfigurationParsingException pe) {
            throw new ConfigurationParsingException("Invalid SpEL in '%s': %s".formatted(key, pe.getMessage()));
        }
    }

    private Object parseNumericOrThrow(ParsingContext<?> ctx, String key, Class<?> wanted, String value, JsonNode node) {
        try {
            if (isInteger(wanted))
                return parseInt(value);
            if (isLong(wanted))
                return parseLong(value);
            if (isDouble(wanted))
                return parseDouble(value);
        } catch (NumberFormatException nfe) {
            var e = new ConfigurationParsingException("Invalid value for '%s': expected %s, but got '%s'. If you meant SpEL, use \"#{...}\" (e.g. \"#{env('PORT')}\").".formatted(key, wanted.getSimpleName(), value));
            if (ctx != null)
                e.setParsingContext(ctx.key(key));
            throw e;
        }
        throw unsupported(wanted, key, node);
    }

    private static boolean isBeanReference(Class<?> wanted) {
        if (wanted == Integer.TYPE || wanted == Long.TYPE || wanted == Float.TYPE || wanted == Double.TYPE || wanted == Boolean.TYPE || wanted == String.class)
            return false;
        return !wanted.isEnum();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E parseEnum(Class<?> enumClass, JsonNode node) throws WrongEnumConstantException {
        String value = node.asText().toUpperCase(ROOT);
        try {
            return Enum.valueOf((Class<E>) enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new WrongEnumConstantException(enumClass, value);
        }
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
