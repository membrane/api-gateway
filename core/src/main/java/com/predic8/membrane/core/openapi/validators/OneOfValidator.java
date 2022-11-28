package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;

public class OneOfValidator {

    private final OpenAPI api;
    private final Schema schema;

    public OneOfValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        List<Schema> oneOfSchemas = schema.getOneOf();

        AtomicInteger numberValid = new AtomicInteger();
        oneOfSchemas.forEach(schema -> {
             if (!areThereErrors(new SchemaValidator(api,schema).validate(ctx,obj))) {
                 numberValid.incrementAndGet();
             }
        });

        if (numberValid.get() == 1) {
            return null;
        }
        return ValidationErrors.create(ctx,String.format("OneOf requires that exactly one subschema is valid. But there are %d subschemas valid.",numberValid.get()));
    }


}
