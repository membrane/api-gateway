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

package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;

import java.util.regex.*;

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;
import static java.lang.String.*;

public class StringRestrictionValidator {

    @SuppressWarnings("rawtypes")
    private final Schema schema;

    @SuppressWarnings("rawtypes")
    public StringRestrictionValidator(Schema schema) {
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object value) {

        // if value is null there is nothing to validate. Null check is done
        // somewhere else.
        if (value == null)
            return null;

        ctx = ctx.schemaType(schema.getType());

        switch (value) {
            case ObjectNode ignored -> {
                return null;
            }
            case ArrayNode ignored -> {
                return null;
            }
            case IntNode ignored -> {
                return null;
            }
            case BooleanNode ignored -> {
                return null;
            }
            case NumericNode ignored -> {
                return null;
            }
            default -> {
            }
        }

        var err = new ValidationErrors();
        var str = getStringValue(value);

        if (isMaxlenExceeded(str)) {
            err.add(new ValidationError(ctx.schemaType(STRING), format("The string '%s' is %d characters long. MaxLength of %d is exceeded.", str, str.length(), schema.getMaxLength())));
        }
        if (isMinLenExceeded(str)) {
            err.add(new ValidationError(ctx.schemaType(STRING), format("The string '%s' is %d characters long. The length of the string is shorter than the minLength of %d.", str, str.length(), schema.getMinLength())));
        }
        if (isPatternViolated(str)) {
            err.add(new ValidationError(
                    ctx.schemaType(STRING),
                    format("The string '%s' does not match the pattern '%s'.", str, schema.getPattern())
            ));
        }
        if (isEnumViolated(str)) {
            err.add(new ValidationError(ctx.schemaType(STRING),
                    format("The string '%s' is not one of the allowed values %s.", str, schema.getEnum())));
        }
        if (isConstViolated(str)) {
            err.add(new ValidationError(ctx.schemaType(STRING),
                    format("The string '%s' must be equal to the constant value '%s'.", str, schema.getConst())));
        }
        return err;
    }

    private boolean isMinLenExceeded(String str) {
        return schema.getMinLength() != null && str.length() < schema.getMinLength();
    }

    private boolean isMaxlenExceeded(String str) {
        return schema.getMaxLength() != null && str.length() > schema.getMaxLength();
    }

    private String getStringValue(Object value) {
        String str = null;
        if (value instanceof String) {
            str = (String) value;
        }
        if (value instanceof TextNode) {
            str = ((TextNode) value).asText();
        }
        return str;
    }

    private boolean isPatternViolated(String str) {
        if (schema.getPattern() == null)
            return false;

        try {
            // OpenAPI/JSON Schema semantics: the regex must match somewhere in the string (not necessarily the whole string).
            return !Pattern.compile(schema.getPattern()).matcher(str).find();
        } catch (PatternSyntaxException e) {
            // Invalid regex in spec: treat as validation error (spec/config issue).
            return true;
        }
    }

    private boolean isEnumViolated(String str) {
        if (schema.getEnum() == null || schema.getEnum().isEmpty())
            return false;

        for (Object v : schema.getEnum()) {
            if (v != null && str.equals(v.toString()))
                return false;
        }
        return true;
    }

    private boolean isConstViolated(String str) {
        if (schema.getConst() == null)
            return false;

        return !str.equals(String.valueOf(schema.getConst()));
    }
}
