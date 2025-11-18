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

import java.math.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.*;
import static java.lang.String.*;
import static java.math.BigDecimal.*;

public class NumberRestrictionValidator {

    @SuppressWarnings("rawtypes")
    private final Schema schema;

    @SuppressWarnings("rawtypes")
    public NumberRestrictionValidator(Schema schema) {
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {

        ctx = ctx.schemaType(schema.getType());

        if (obj instanceof ObjectNode)
            return null;
        if (obj instanceof ArrayNode)
            return null;
        if (obj instanceof BooleanNode)
            return null;

        BigDecimal value;
        try {
            value = convertToBigDecimal(obj);
        } catch (NumberFormatException e) {
            // if value is not parsable as a number then a check of number restrictions is not needed
            return null;
        }

        ValidationErrors errors = new ValidationErrors();
        errors.add(validateMinimum(ctx, value));
        errors.add(validateExclusiveMinimum(ctx, value));
        errors.add(validateMaximum(ctx, value));
        errors.add(validateExclusiveMaximum(ctx, value));
        errors.add(validateMultipleOf(ctx, value));
        return errors;
    }

    private ValidationErrors validateMultipleOf(ValidationContext ctx, BigDecimal value) {
        if (schema.getMultipleOf() == null)
            return null;

        BigDecimal multiplesOf = schema.getMultipleOf();
        BigDecimal remainder = value.remainder(multiplesOf).stripTrailingZeros();
        if (remainder.compareTo(ZERO) != 0) {
            return error(ctx, format("Value %s is not a multiple of %s.", value, multiplesOf));
        }

        return null;
    }

    private ValidationErrors validateExclusiveMaximum(ValidationContext ctx, BigDecimal value) {
        if (schema.getExclusiveMaximumValue() != null) {
            if (schema.getExclusiveMaximumValue().compareTo(value) < 0) {
                return error(ctx, value + " is greater than the maximum of " + schema.getExclusiveMaximumValue());
            }
            if (schema.getExclusiveMaximumValue().compareTo(value) == 0) {
                return error(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getExclusiveMaximumValue()));
            }
        }
        return null;
    }

    private ValidationErrors validateMaximum(ValidationContext ctx, BigDecimal value) {
        if (schema.getMaximum() != null) {
            if (schema.getMaximum().compareTo(value) < 0) {
                return error(ctx, value + " is greater than the maximum of " + schema.getMaximum());
            }
            if (isExclusiveMaximum() && schema.getMaximum().compareTo(value) == 0) {
                return error(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getMaximum()));
            }
        }
        return null;
    }

    private ValidationErrors validateExclusiveMinimum(ValidationContext ctx, BigDecimal value) {
        if (schema.getExclusiveMinimumValue() != null) {
            if (schema.getExclusiveMinimumValue().compareTo(value) > 0) {
                return error(ctx, value + " is smaller than the minimum of " + schema.getExclusiveMinimumValue());
            }
            if (schema.getExclusiveMinimumValue().compareTo(value) == 0) {
                return error(ctx, format("The value of %s should be greater than the exclusive minimum %s.", value, schema.getExclusiveMinimumValue()));
            }
        }
        return null;
    }

    private ValidationErrors validateMinimum(ValidationContext ctx, BigDecimal value) {
        if (schema.getMinimum() != null) {
            if (schema.getMinimum().compareTo(value) > 0) {
                return error(ctx, value + " is smaller than the minimum of " + schema.getMinimum());
            }
            if (isExclusiveMinimum() && schema.getMinimum().compareTo(value) == 0) {
                return error(ctx, format("The value of %s should be greater than the exclusive minimum %s.", value, schema.getMinimum()));
            }
        }
        return null;
    }

    private boolean isExclusiveMinimum() {
        return schema.getExclusiveMinimum() != null && schema.getExclusiveMinimum();
    }

    private boolean isExclusiveMaximum() {
        return schema.getExclusiveMaximum() != null && schema.getExclusiveMaximum();
    }

}
