package com.predic8.membrane.core.openapi.validators;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;

public class NotValidator {

    private final OpenAPI api;
    private final Schema schema;

    public NotValidator(OpenAPI api, Schema schema) {
        this.api = api;
        this.schema = schema;
    }

    public ValidationErrors validate(ValidationContext ctx, Object obj) {
        Schema notSchema = schema.getNot();

        ValidationErrors ve = new SchemaValidator(api,notSchema).validate(ctx,obj);
        if (ve.size() > 0)
            return null;

        return ValidationErrors.create(ctx,"Subschema is declared with not. Should not validate against subschema.");
    }


}
