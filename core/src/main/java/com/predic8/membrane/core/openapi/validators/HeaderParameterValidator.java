package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.QueryParameter;

import java.util.Map;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;

public class HeaderParameterValidator {

    OpenAPI api;
    PathItem pathItem;

    public HeaderParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    ValidationErrors validateHeaderParameters(ValidationContext ctx, Request request, Operation operation)  {

        ValidationErrors errors = new ValidationErrors();

        // TODO
        // Router?

        Map<String, String> headers = request.getHeaders();

/*        getAllParameterSchemas(operation).forEach(param -> {
            errors.add(validateQueryParameter(ctx.entity(param.getName()).entityType(QUERY_PARAMETER), hparams, param));
            hparams.remove(param.getName()); // Delete param so there should't be any parameter left
        });

        errors.add(checkForAdditionalQueryParameters(ctx, hparams));*/

        return errors;
    }

}
