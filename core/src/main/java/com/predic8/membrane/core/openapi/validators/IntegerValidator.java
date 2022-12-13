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
import com.predic8.membrane.core.openapi.model.*;
import io.swagger.v3.oas.models.media.*;

import java.io.*;
import java.math.*;

import static java.lang.Long.parseLong;

public class IntegerValidator implements IJSONSchemaValidator {

    /**
     * Only check if value can be parsed as an integer.
     * @param ctx
     * @param value
     * @return
     */
    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {

        if (value instanceof JsonNode) {
            value = ((JsonNode) value).numberValue();
        }

        try {
            if (value instanceof String) {
                parseLong((String) value);
                return null;
            }
            if (value instanceof Integer) {
                return null;
            }
            if (value instanceof Body)
                return null;

            if (value != null)
                throw new RuntimeException("Do not know type " + value.getClass());
            throw new RuntimeException("Value is null!");

        } catch (NumberFormatException e) {
            return ValidationErrors.create(ctx.schemaType("integer"), String.format("%s is not an integer.", value));
        }
    }
}

