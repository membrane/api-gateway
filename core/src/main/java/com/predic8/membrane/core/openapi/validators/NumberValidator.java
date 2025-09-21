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

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.*;

import static java.lang.Double.*;

/**
 * When numbers appear in parameters, they enter as Strings (which is OK).
 *
 * If numbers appear in a JSON string "123.45", this is a TextNode (which is not OK). (See JSON Schema Spec.)
 */
public class NumberValidator implements JsonSchemaValidator {

    @Override
    public String canValidate(Object value) {
        try {
            if (value instanceof Number) {
                return NUMBER;
            }
            if (value instanceof TextNode) {
                return null;
            }
            if (value instanceof JsonNode jn) {
                new BigDecimal((jn).asText());
                return NUMBER;
            }
            if (value instanceof String s) {
                parseDouble(s);
                return NUMBER;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    /**
     * Only check if obj can be converted to a number
     */
    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {
        try {
            if (value instanceof TextNode) {
                return ValidationErrors.create(ctx.schemaType(NUMBER), String.format("%s is not a number.", value));
            }
            if (value instanceof JsonNode jn) {
                // Not using double prevents from losing fractions
                new BigDecimal(jn.asText());
                return null;
            }
            if (value instanceof String s) {
                parseDouble(s);
                return null;
            }
        } catch (NumberFormatException ignored) {
            return ValidationErrors.create(ctx.schemaType(NUMBER), String.format("%s is not a number.", value));
        }
        throw new RuntimeException("Should never happen!");
    }
}
