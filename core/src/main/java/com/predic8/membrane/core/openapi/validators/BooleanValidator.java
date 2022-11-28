package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

public class BooleanValidator implements IJSONSchemaValidator {

    private final Schema schema;

    public BooleanValidator(Schema schema) {
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object value) {

        ValidationErrors errors = new ValidationErrors();

        if (value instanceof BooleanNode)
            return errors;

        String str = "";
        if (value instanceof TextNode) {
            str = ((TextNode) value).asText();
        } else if (value instanceof String) {
            str = (String) value;
        }

        if (str.equals("true") || str.equals("false"))
            return errors;

        errors.add(ctx,String.format("Value '%s' is not a boolean (true/false).",value));

        return errors;
    }
}
