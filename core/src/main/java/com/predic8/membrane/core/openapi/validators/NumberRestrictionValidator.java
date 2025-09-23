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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.media.Schema;

import java.math.BigDecimal;

import static com.predic8.membrane.core.openapi.util.Utils.convertToBigDecimal;
import static java.lang.String.format;

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
        BigDecimal[] remainder = value.divideAndRemainder(multiplesOf);
        if (remainder[1].intValue() != 0) {
            return ValidationErrors.error(ctx, String.format("Value %d is not a multiple of %d.", value.intValue(), multiplesOf.intValue()));
        }

        return null;
    }

    private ValidationErrors validateExclusiveMaximum(ValidationContext ctx, BigDecimal value) {
        if (schema.getExclusiveMaximumValue() != null) {
            if (schema.getExclusiveMaximumValue().compareTo(value) < 0) {
                return ValidationErrors.error(ctx, value + " is greater than the maximum of " + schema.getExclusiveMaximumValue());
            }
            if (schema.getExclusiveMaximumValue().compareTo(value) == 0) {
                return ValidationErrors.error(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getExclusiveMaximumValue()));
            }
        }
        return null;
    }

    private ValidationErrors validateMaximum(ValidationContext ctx, BigDecimal value) {
        if (schema.getMaximum() != null) {
            if (schema.getMaximum().compareTo(value) < 0) {
                return ValidationErrors.error(ctx, value + " is greater than the maximum of " + schema.getMaximum());
            }
            if (isExclusiveMaximum() && schema.getMaximum().compareTo(value) == 0) {
                return ValidationErrors.error(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getMaximum()));
            }
        }
        return null;
    }

    private ValidationErrors validateExclusiveMinimum(ValidationContext ctx, BigDecimal value) {
        if(schema.getExclusiveMinimumValue() != null) {
           if (schema.getExclusiveMinimumValue().compareTo(value) > 0) {
               return ValidationErrors.error(ctx, value + " is smaller than the minimum of " + schema.getExclusiveMinimumValue());
            }
            if (schema.getExclusiveMinimumValue().compareTo(value) == 0) {
                return ValidationErrors.error(ctx, format("The value of %s should be greater than the exclusive minimum. %s", value, schema.getExclusiveMinimumValue()));
            }
        }
        return null;
    }

    private ValidationErrors validateMinimum(ValidationContext ctx, BigDecimal value) {
        if (schema.getMinimum() != null) {
            if (schema.getMinimum().compareTo(value) > 0) {
                return ValidationErrors.error(ctx, value + " is smaller than the minimum of " + schema.getMinimum());
            }
            if (isExclusiveMinimum() && schema.getMinimum().compareTo(value) == 0) {
                return ValidationErrors.error(ctx, format("The value of %s should be greater than the exclusive minimum %s.", value, schema.getMinimum()));
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
