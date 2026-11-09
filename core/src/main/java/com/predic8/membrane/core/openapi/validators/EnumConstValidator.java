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

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;

import java.math.*;
import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static com.predic8.membrane.core.openapi.validators.ValidationError.v;

/**
 * Validates enum and const for values of every type but string. Strings are checked by
 * {@link StringRestrictionValidator}.
 * <p>
 * Values are compared as JSON: numbers by their value, so 1 equals 1.0, also inside arrays and
 * objects. A query parameter is text, so there ?n=42 also matches the string "42".
 */
public class EnumConstValidator {

    private static final Comparator<JsonNode> NUMBERS_BY_VALUE = (a, b) -> {
        if (a.isNumber() && b.isNumber())
            return a.decimalValue().compareTo(b.decimalValue());
        return a.equals(b) ? 0 : 1;
    };

    @SuppressWarnings("rawtypes")
    private final Schema schema;

    @SuppressWarnings("rawtypes")
    public EnumConstValidator(Schema schema) {
        this.schema = schema;
    }

    public boolean hasEnum() {
        return schema.getEnum() != null && !schema.getEnum().isEmpty();
    }

    /**
     * @return true if the value is one of the members of the enum, compared as JSON
     */
    public boolean isEnumMember(Object value) {
        if (!hasEnum())
            return false;
        final var node = toJsonNode(value);
        for (Object member : schema.getEnum()) {
            if (isEqual(toJsonNode(member), node))
                return true;
        }
        return false;
    }

    public ValidationErrors validate(ValidationContext ctx, Object value) {
        if (isString(value))
            return null;

        final var node = toJsonNode(value);
        final var errors = new ValidationErrors();
        if (hasEnum() && schema.getEnum().stream().noneMatch(member -> matches(ctx, toJsonNode(member), node)))
            errors.add(ctx, mask -> "%s is not part of the enum %s.".formatted(v(value, mask), schema.getEnum()));
        if (schema.getConst() != null && !matches(ctx, toJsonNode(schema.getConst()), node))
            errors.add(ctx, mask -> "%s must be equal to the constant value %s.".formatted(v(value, mask), schema.getConst()));
        return errors;
    }

    static boolean isString(Object value) {
        return value instanceof String || value instanceof TextNode;
    }

    private static boolean matches(ValidationContext ctx, JsonNode expected, JsonNode value) {
        if (isEqual(expected, value))
            return true;
        return QUERY_PARAMETER.equals(ctx.getValidatedEntityType())
               && expected.isValueNode() && value.isValueNode()
               && expected.asText().equals(value.asText());
    }

    private static boolean isEqual(JsonNode a, JsonNode b) {
        return a.equals(NUMBERS_BY_VALUE, b);
    }

    /**
     * The parser keeps enum members and const as Number, Boolean, String, JsonNode or null.
     */
    private static JsonNode toJsonNode(Object value) {
        return switch (value) {
            case null -> NullNode.getInstance();
            case JsonNode node -> node;
            case Number number -> JsonNodeFactory.instance.numberNode(new BigDecimal(number.toString()));
            case Boolean bool -> BooleanNode.valueOf(bool);
            default -> TextNode.valueOf(value.toString());
        };
    }
}
