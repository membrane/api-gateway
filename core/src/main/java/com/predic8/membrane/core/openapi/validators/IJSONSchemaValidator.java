package com.predic8.membrane.core.openapi.validators;

interface IJSONSchemaValidator {
    ValidationErrors validate(ValidationContext ctx, Object value);
}
