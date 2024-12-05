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

import java.math.*;

import static java.lang.Double.*;

public class NumberValidator implements IJSONSchemaValidator {

    @Override
    public String canValidate(Object value) {
        try {
            if (value instanceof Number) {
                return NUMBER;
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
