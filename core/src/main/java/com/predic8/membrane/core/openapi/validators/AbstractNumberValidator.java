package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.media.*;

import java.math.*;

import static java.lang.String.format;

public abstract class AbstractNumberValidator implements IJSONSchemaValidator {

    protected final Schema schema;

    public AbstractNumberValidator(Schema schema) {
        this.schema = schema;
    }

    /**
     * Number and Integer share the same restrictions
     * <p>
     * multipleOf
     * minimum
     * exclusiveMaximum
     * exclusiveMinimum
     *
     * @param value
     * @param ctx
     * @return
     */
    public ValidationErrors validateRestrictions(BigDecimal value, ValidationContext ctx) {

        ValidationErrors errors = new ValidationErrors();
        ;


        return errors;
    }


}
