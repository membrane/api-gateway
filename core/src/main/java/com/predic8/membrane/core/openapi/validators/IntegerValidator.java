package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;
import io.swagger.v3.oas.models.media.*;

import java.io.*;
import java.math.*;

import static java.lang.Long.parseLong;

public class IntegerValidator extends AbstractNumberValidator {

    public IntegerValidator(Schema schema) {
        super(schema);
    }

    @Override
    public ValidationErrors validate(ValidationContext ctx, Object value) {

        if (value instanceof JsonNode) {
            value = ((JsonNode) value).numberValue();
        }

        ValidationErrors errors = new ValidationErrors();;
        long longValue = 0;

        try {
            if (value instanceof String) {
                longValue = parseLong((String) value);
            } else if (value instanceof Integer) {
                longValue = ((Integer) value);
            } else if(value instanceof Body) {
                longValue = parseLong(((Body) value).asString());
            } else {
                if (value != null)
                    throw new RuntimeException("Do not know type " + value.getClass());
                throw new RuntimeException("Value is null!");
            }
        } catch (NumberFormatException | IOException e) {
            errors.add(new ValidationError(ctx.schemaType("integer"), String.format("%s is not an integer.", value)));
            return errors;
        }

        errors.add(validateRestrictions(new BigDecimal(longValue), ctx));

        return errors;
    }

}

