/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;

@SuppressWarnings("rawtypes")
public class SchemaUtil {

    /**
     * Compiled {@code patternProperties} regexes by regex text, shared JVM-wide: {@link Pattern} is immutable
     * and thread safe. Invalid regexes are not stored. See {@link #compilePatternPropertyRegex(String)}.
     */
    private static final Map<String, Pattern> PATTERN_PROPERTY_PATTERNS = new ConcurrentHashMap<>();

    /**
     * Longest text that is parsed as a number, the limit Jackson applies to numbers in JSON bodies by default
     * ({@code StreamReadConstraints}). Parsing a number exactly takes quadratic time in its length.
     */
    public static final int MAX_NUMBER_LENGTH = 1000;

    /**
     * Looks up the schema referenced by {@code ref} under {@code #/components/schemas}.
     *
     * @return the referenced schema, or {@code null} if no schema with that name exists
     * @throws OpenAPIParsingException if the document has no {@code #/components} or
     *                                 {@code #/components/schemas} section
     */
    public static Schema getSchemaFromRef(OpenAPI api, String ref) {

        Components components = api.getComponents();
        if (components == null)
            throw new OpenAPIParsingException("OpenAPI %s has no #/components field.".formatted(api.getInfo().getTitle()));

        Map<String, Schema> schemas = components.getSchemas();
        if(schemas == null)
            throw new OpenAPIParsingException("OpenAPI %s has no #/components/schemas field.".formatted(api.getInfo().getTitle()));

        ObjectHolder<Schema> oh = new ObjectHolder<>();
        schemas.forEach((schemaName, refSchema) -> {
            if (schemaName.equals(getComponentLocalNameFromRef(ref))) {
                oh.setValue(refSchema);
            }

        });
        return oh.getValue();
    }

    /**
     * Returns the local component name of the schema's {@code $ref}, e.g. {@code Customer} for
     * {@code #/components/schemas/Customer}.
     */
    public static String getSchemaNameFromRef(Schema schema) {
        return getComponentLocalNameFromRef(schema.get$ref());
    }

    /**
     * Resolves a {@code $ref} to the referenced schema; returns {@code schema} unchanged if it has no ref.
     */
    public static Schema resolveRef(OpenAPI api, Schema schema) {
        if (schema == null || schema.get$ref() == null)
            return schema;
        return getSchemaFromRef(api, schema.get$ref());
    }

    /**
     * Returns the effective type of a schema, handling both OAS 3.0 ({@code type}) and OAS 3.1
     * ({@code types}). If multiple types are declared the first non-"null" type wins.
     */
    public static String getEffectiveType(Schema schema) {
        if (schema.getType() != null)
            return schema.getType();
        var types = schema.getTypes();
        if (types == null)
            return null;
        for (Object type : types) {
            if (!"null".equals(type))
                return type.toString();
        }
        return null;
    }

    /** True if the schema's type is {@code string}, whether declared via OAS 3.0 {@code type} or OAS 3.1 {@code types}. */
    public static boolean isString(Schema schema) {
        if ("string".equals(schema.getType()))
            return true;
        return schema.getTypes() != null && schema.getTypes().contains("string");
    }

    /** A string schema with {@code format: binary} or {@code byte}, i.e. opaque binary content. */
    public static boolean isBinaryString(Schema schema) {
        if (!isString(schema))
            return false;
        String format = schema.getFormat();
        return "binary".equals(format) || "byte".equals(format);
    }

    /**
     * Returns {@code additionalProperties} as a {@link Boolean}, a {@link Schema} or {@code null} if absent.
     * OAS 3.1 parses {@code additionalProperties: true/false} into a boolean schema, 3.0 into a Boolean. See #3562.
     */
    public static Object getAdditionalProperties(Schema schema) {
        var additional = schema.getAdditionalProperties();
        if (additional instanceof Schema<?> s && s.getBooleanSchemaValue() != null)
            return s.getBooleanSchemaValue();
        return additional;
    }

    /**
     * Returns the compiled {@code patternProperties} regexes of the schema, or an empty list if it has none.
     *
     * @throws OpenAPIParsingException if a regex is invalid, see {@link #compilePatternPropertyRegex(String)}
     */
    @SuppressWarnings("unchecked")
    public static List<Pattern> getPatternPropertyPatterns(Schema schema) {
        final Map<String, ?> patternProperties = schema.getPatternProperties();
        if (patternProperties == null)
            return List.of();
        return patternProperties.keySet().stream().map(SchemaUtil::compilePatternPropertyRegex).toList();
    }

    /**
     * Compiles a {@code patternProperties} regex. Validators are created per validated value, so compiled
     * patterns are cached by regex. Only regexes of loaded specs become keys, entries are never removed.
     *
     * @throws OpenAPIParsingException if the regex is invalid. That is an error in the spec, not in the
     *                                 validated message, and skipping the pattern would drop its constraints.
     */
    public static Pattern compilePatternPropertyRegex(String regex) {
        return PATTERN_PROPERTY_PATTERNS.computeIfAbsent(regex, r -> {
            try {
                return Pattern.compile(r);
            } catch (PatternSyntaxException e) {
                final var error = new OpenAPIParsingException("Invalid patternProperties regex '%s': %s (index %d)."
                        .formatted(r, e.getDescription(), e.getIndex()));
                error.initCause(e);
                throw error;
            }
        });
    }

    /**
     * Chooses the schema whose type guides decoding of a value that all {@code schemas} apply to, e.g. a
     * property and its matching {@code patternProperties}. Untyped schemas do not decide the type; a type
     * allowed by every typed schema is preferred, so the choice does not depend on declaration order.
     * The schemas must already be resolved.
     */
    public static Schema decodingSchema(List<Schema> schemas) {
        final var typed = schemas.stream().filter(s -> getEffectiveType(s) != null).toList();
        // Prefer a schema whose chosen type is allowed by every other typed constraint.
        final var preferred = typed.stream().filter(candidate -> typed.stream().allMatch(s ->
                        allowsType(s, getEffectiveType(candidate))))
                .findFirst();
        if (preferred.isPresent())
            return preferred.get();
        if (typed.isEmpty())
            return schemas.getFirst();
        final var types = typed.getFirst().getTypes();
        if (types != null)
            for (Object type : types)
                if (!"null".equals(type) && typed.stream().allMatch(s -> allowsType(s, type.toString())))
                    return new Schema<>().type(type.toString());
        return typed.getFirst();
    }

    /** True if {@code type} is the schema's {@code type} or one of its OAS 3.1 {@code types}. */
    public static boolean allowsType(Schema schema, String type) {
        return type.equals(schema.getType()) || schema.getTypes() != null && schema.getTypes().contains(type);
    }

    /** True if the schema's effective type is {@code array}. */
    public static boolean isArray(Schema schema) {
        return "array".equals(getEffectiveType(schema));
    }

    /**
     * Returns the schema of the array item at {@code index}: the {@code prefixItems} entry at that
     * position (JSON Schema 2020-12, OAS 3.1 and later), otherwise {@code items}. The result may be a
     * {@code $ref} or {@code null} if the array does not constrain that item.
     */
    public static Schema itemSchema(Schema arraySchema, int index) {
        List<Schema> prefixItems = arraySchema.getPrefixItems();
        if (prefixItems != null && index < prefixItems.size())
            return prefixItems.get(index);
        return arraySchema.getItems();
    }

    /** True for the primitive scalar types string, integer, number and boolean. */
    public static boolean isScalar(Schema schema) {
        String type = getEffectiveType(schema);
        return "string".equals(type) || "integer".equals(type) || "number".equals(type) || "boolean".equals(type);
    }

    /**
     * True if the schema represents structured content, i.e. its effective type is {@code object} or
     * {@code array}, or it declares {@code properties}. Used to decide whether a part without an explicit
     * content type defaults to JSON rather than {@code text/plain}.
     * <p>
     * A {@code $ref} is resolved against {@code api} first (see {@link #resolveRef}), so the referenced
     * schema's type is what gets inspected. A ref that cannot be resolved is reported as not structured.
     */
    public static boolean isObjectOrArray(OpenAPI api, Schema schema) {
        schema = resolveRef(api, schema);
        if (schema == null)
            return false;
        var type = getEffectiveType(schema);
        if ("object".equals(type) || "array".equals(type))
            return true;
        return schema.getProperties() != null;
    }

    /**
     * Parses raw text into a typed JSON value according to the schema's scalar type. If the text does
     * not match the declared numeric/boolean type it is kept as text so the validator can report the
     * type mismatch.
     */
    public static JsonNode parseScalar(String text, Schema schema) {
        JsonNodeFactory nf = JsonNodeFactory.instance;
        String type = getEffectiveType(schema);
        if (type == null)
            return nf.textNode(text);
        return switch (type) {
            case "integer" -> parseInteger(text);
            case "number" -> parseNumber(text);
            case "boolean" -> switch (text.trim()) {
                case "true" -> nf.booleanNode(true);
                case "false" -> nf.booleanNode(false);
                default -> nf.textNode(text);
            };
            default -> nf.textNode(text);
        };
    }

    /**
     * Parses an integer exactly, also beyond the range of {@code long}. Text that is not an integer or longer than
     * {@link #MAX_NUMBER_LENGTH} is kept as text, so the validator reports it.
     */
    public static JsonNode parseInteger(String text) {
        final var trimmed = text.trim();
        if (trimmed.length() <= MAX_NUMBER_LENGTH) {
            try {
                final var value = new BigInteger(trimmed);
                return value.bitLength() < Long.SIZE
                        ? JsonNodeFactory.instance.numberNode(value.longValue())
                        : JsonNodeFactory.instance.numberNode(value);
            } catch (NumberFormatException ignored) {
                // Not an integer
            }
        }
        return JsonNodeFactory.instance.textNode(text);
    }

    /**
     * Parses a number exactly, without rounding it to a {@code double}. Text that is not a number, e.g.
     * {@code NaN} or {@code 1d}, or longer than {@link #MAX_NUMBER_LENGTH} is kept as text, so the validator
     * reports it.
     */
    public static JsonNode parseNumber(String text) {
        final var trimmed = text.trim();
        if (trimmed.length() <= MAX_NUMBER_LENGTH) {
            try {
                return JsonNodeFactory.instance.numberNode(new BigDecimal(trimmed));
            } catch (NumberFormatException ignored) {
                // Not a number
            }
        }
        return JsonNodeFactory.instance.textNode(text);
    }
}
