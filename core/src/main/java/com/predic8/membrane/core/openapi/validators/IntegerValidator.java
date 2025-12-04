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

import tools.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;

import java.math.*;

import static com.predic8.membrane.core.openapi.validators.ValidationErrors.error;

public class IntegerValidator implements JsonSchemaValidator {

    @Override
    public String canValidate(Object obj) {
        if (obj instanceof JsonNode j) {
            return j.isIntegralNumber() ? INTEGER : null;
        }
        if (obj instanceof String s) {
            try {
                new BigInteger(s);
                return INTEGER;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (obj instanceof Byte || obj instanceof Short || obj instanceof Integer || obj instanceof Long || obj instanceof BigInteger) {
            return INTEGER;
        }
        return null;
    }

    public ValidationErrors validate(ValidationContext ctx, Object value) {
        if (value instanceof JsonNode j) {
            return j.isIntegralNumber() ? null
                    : error(ctx.schemaType("integer"),
                    String.format("%s is not an integer.", j.asText()));
        }
        if (value instanceof String s) {
            try {
                new BigInteger(s);
                return null;
            } catch (NumberFormatException e) {
                return error(ctx.schemaType("integer"),
                        String.format("%s is not an integer.", s));
            }
        }
        if (value instanceof Body) {
            return null;
        }
        // Numeric types (Byte/Short/Integer/Long/BigInteger) are already validated by canValidate()
        if (canValidate(value) != null) {
            return null;
        }
        if (value instanceof Number) {
            return error(ctx.schemaType("integer"), "%s is not an integer.".formatted(value));
        }
        if (value == null) {
            return error(ctx.schemaType("integer"), "null is not an integer.");
        }
        return error(ctx.schemaType("integer"),
                String.format("Do not know type %s for integer validation.", value.getClass().getName()));
    }
}

