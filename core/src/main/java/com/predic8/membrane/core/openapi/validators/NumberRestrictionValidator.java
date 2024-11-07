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
import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;

import java.math.*;

import static java.lang.Double.parseDouble;
import static java.lang.String.*;

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

        ValidationErrors errors = new ValidationErrors();

        try {
            if (obj instanceof JsonNode) {
                // Not using double prevents from losing fractions
                obj = new BigDecimal(((JsonNode) obj).asText());
            } else if (obj instanceof String) {
                obj = BigDecimal.valueOf(parseDouble((String) obj));
            }
        } catch (NumberFormatException e) {
            return errors;
        }

        BigDecimal value = (BigDecimal) obj;

        validateMinimum(ctx, value, errors);
        validateMaximum(ctx, value, errors);

        if (schema.getMultipleOf() != null) {
            BigDecimal multiplesOf = schema.getMultipleOf();
            BigDecimal[] remainder = value.divideAndRemainder(multiplesOf);
            if (remainder[1].intValue() != 0) {
                errors.add(ctx,String.format("Value %d is not a multiple of %d.",value.intValue(),multiplesOf.intValue()));
            }
        }

        return errors;
    }

    private void validateMaximum(ValidationContext ctx, BigDecimal value, ValidationErrors errors) {
        if (schema.getMaximum() != null) {
            if (schema.getMaximum().compareTo(value) < 0) {
                errors.add(new ValidationError(ctx, value + " is greater than the maximum of " + schema.getMaximum()));
            }
            if (isExclusiveMaximum() && schema.getMaximum().compareTo(value) == 0) {
                errors.add(new ValidationError(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getMaximum())));
            }
        }

        if (schema.getExclusiveMaximumValue() != null) {
            if (schema.getExclusiveMaximumValue().compareTo(value) < 0) {
                errors.add(new ValidationError(ctx, value + " is greater than the maximum of " + schema.getExclusiveMaximumValue()));
            }
            if (schema.getExclusiveMaximumValue().compareTo(value) == 0) {
                errors.add(new ValidationError(ctx, format("The value of %s should be less than the exclusive maximum %s.", value, schema.getExclusiveMaximumValue())));
            }
        }
    }

    private void validateMinimum(ValidationContext ctx, BigDecimal value, ValidationErrors errors) {
        if (schema.getMinimum() != null) {
            if (schema.getMinimum().compareTo(value) > 0) {
                errors.add(new ValidationError(ctx, value + " is smaller than the minimum of " + schema.getMinimum()));
            }
            if (isExclusiveMinimum() && schema.getMinimum().compareTo(value) == 0) {
                errors.add(new ValidationError(ctx, format("The value of %s should be greater than the exclusive minimum %s.", value, schema.getMinimum())));
            }
        }

        if(schema.getExclusiveMinimumValue() != null) {
           if (schema.getExclusiveMinimumValue().compareTo(value) > 0) {
                errors.add(new ValidationError(ctx, value + " is smaller than the minimum of " + schema.getExclusiveMinimumValue()));
            }
            if (schema.getExclusiveMinimumValue().compareTo(value) == 0) {
                errors.add(new ValidationError(ctx, format("The value of %s should be greater than the exclusive minimum. %s", value, schema.getExclusiveMinimumValue())));
            }
        }
    }

    private boolean isExclusiveMinimum() {
        return schema.getExclusiveMinimum() != null && schema.getExclusiveMinimum();
    }

    private boolean isExclusiveMaximum() {
        return schema.getExclusiveMaximum() != null && schema.getExclusiveMaximum();
    }

}
