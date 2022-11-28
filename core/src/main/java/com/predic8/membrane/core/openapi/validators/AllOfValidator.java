package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;

public class AllOfValidator {

    private final OpenAPI api;
    private final Schema schema;

    public AllOfValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        ValidationErrors errors = new ValidationErrors();
        List<Schema> allOfSchemas = schema.getAllOf();
        allOfSchemas.forEach(schema -> {
             errors.add(new SchemaValidator(api,schema).validate(ctx,obj));
        });
        if (errors.size() > 0) {
            errors.add(ctx,String.format("One of the subschemas of allOf is not valid."));
        }
        return errors;
    }
}
