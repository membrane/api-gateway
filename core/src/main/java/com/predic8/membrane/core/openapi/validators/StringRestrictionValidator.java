package com.predic8.membrane.core.openapi.validators;

import com.fasterxml.jackson.databind.node.*;
import io.swagger.v3.oas.models.media.*;

import static java.lang.String.*;

public class StringRestrictionValidator {

    private final Schema schema;

    public StringRestrictionValidator(Schema schema) {
        this.schema = schema;
    }


    public ValidationErrors validate(ValidationContext ctx, Object value) {

        ctx = ctx.schemaType(schema.getType());

        if (value instanceof ObjectNode)
            return null;
        if (value instanceof ArrayNode)
            return null;
        if (value instanceof IntNode)
            return null;
        if (value instanceof BooleanNode)
            return null;
        if (value instanceof DoubleNode)
            return null;
        if (value instanceof DecimalNode)
            return null;

        ValidationErrors errors = new ValidationErrors();

        String str = null;
        if (value instanceof String) {
            str = (String) value;
        }
        if (value instanceof TextNode) {
            str = ((TextNode)value).asText();
        }

        if (schema.getMaxLength() != null) {
            if (str.length() > schema.getMaxLength()) {
                errors.add(new ValidationError(ctx.schemaType("string"), format("The string '%s' is %d characters long. MaxLength of %d is exceeded.", str, str.length(), schema.getMaxLength())));
            }
        }
        if (schema.getMinLength() != null) {
            if (str.length() < schema.getMinLength()) {
                errors.add(new ValidationError(ctx.schemaType("string"), format("The string '%s' is %d characters long. The length of the string is shorter than the minLength of %d.", str, str.length(), schema.getMinLength())));
            }
        }

        return errors;
    }
}
