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

        if (value instanceof ObjectNode)
            return null;
        if (value instanceof ArrayNode)
            return null;
        if (value instanceof IntNode)
            return null;
        if (value instanceof BooleanNode)
            return null;
        if (value instanceof DoubleNode)
            return null;
        if (value instanceof DecimalNode)
            return null;

        ValidationErrors errors = new ValidationErrors();
        String str = getStringValue(value);

        if (isMaxlenExceeded(str)) {
            errors.add(new ValidationError(ctx.schemaType("string"), format("The string '%s' is %d characters long. MaxLength of %d is exceeded.", str, str.length(), schema.getMaxLength())));
        }
        if (isMinLenExceeded(str)) {
            errors.add(new ValidationError(ctx.schemaType("string"), format("The string '%s' is %d characters long. The length of the string is shorter than the minLength of %d.", str, str.length(), schema.getMinLength())));
        }
        return errors;
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
}
