package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.areThereErrors;

public class AnyOfValidator {

    private final OpenAPI api;
    private final Schema schema;

    public AnyOfValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        List<Schema> anyOfSchemas = schema.getAnyOf();

        AtomicBoolean oneIsValid = new AtomicBoolean();
        anyOfSchemas.forEach(schema -> {
             if (!areThereErrors(new SchemaValidator(api,schema).validate(ctx,obj))) {
                 oneIsValid.set(true);
             }
        });

        if (oneIsValid.get()) {
            return null;
        }
        return ValidationErrors.create(ctx,"None of the subschemas of anyOf was true.");
    }


}
