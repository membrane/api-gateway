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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.math.BigDecimal;

import static java.lang.Double.parseDouble;

public class NullValidator implements IJSONSchemaValidator {

    @Override
    public String canValidate(Object value) {
        if (value == null) {
            return NULL;
        }
        if (value instanceof NullNode) {
            return NULL;
        }
        return null;
    }

    /**
     * Only check if obj can be converted to a number
     */
    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof NullNode) {
            return null;
        }
        return ValidationErrors.create(ctx.schemaType(NULL), String.format("%s is not null.", value));
    }
}
