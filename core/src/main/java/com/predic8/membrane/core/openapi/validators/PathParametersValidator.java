package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH_PARAMETER;

public class PathParametersValidator {

    OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public PathParametersValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validatePathParameters(ValidationContext ctx, Request req, List<Parameter> schemaParameters) {

        if (schemaParameters == null || req.getPathParameters().size() == 0)
            return null;

        schemaParameters.stream().filter(this::isPathParameter).forEach(parameter -> {
            String value = req.getPathParameters().get(parameter.getName());
            if (value == null) {
                throw new RuntimeException("Should not happen!");
            }
            errors.add(new SchemaValidator(api, parameter.getSchema()).validate(ctx.validatedEntityType(PATH_PARAMETER)
                    .validatedEntity(parameter.getName())
                    .path(req.getPath())
                    .statusCode(400), value));
        });
        return errors;
    }

    private boolean isPathParameter(Parameter p) {
        return p instanceof PathParameter;
    }
}
