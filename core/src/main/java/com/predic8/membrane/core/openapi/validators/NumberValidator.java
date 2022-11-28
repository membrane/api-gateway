package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import io.swagger.v3.oas.models.media.*;

import java.math.*;

public class NumberValidator extends AbstractNumberValidator {

    public NumberValidator(Schema schema) {
        super(schema);
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object obj) {

        ctx = ctx.schemaType("number");

        ValidationErrors errors = new ValidationErrors();
        BigDecimal value = null;

        try {
            if (obj instanceof JsonNode) {
                // Not using double prevents from losing fractions
                value = new BigDecimal(((JsonNode) obj).asText());
            } else if (obj instanceof String) {
                value = new BigDecimal(Double.parseDouble((String) obj));
            }
        } catch (NumberFormatException e) {
            return errors.add(new ValidationError(ctx, String.format("%s is not a number.", obj)));
        }


        return errors.add(validateRestrictions(value, ctx));
    }
}
