package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.Request;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.HEADER_PARAMETER;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static com.predic8.membrane.core.util.CollectionsUtil.concat;
import static java.lang.String.format;

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

        getAllParameterSchemas(operation).forEach(param -> {
            if (!(param instanceof HeaderParameter)) {
                return;
            }
            errors.add(validateHeaderParameter(ctx.entity(param.getName()).entityType(QUERY_PARAMETER), headers, param));
            headers.remove(param.getName()); // Delete param so there shouldn't be any parameter left
        });
        return errors;
    }

    public List<Parameter> getAllParameterSchemas(Operation operation) {
        return concat(resolveRefs(pathItem.getParameters()), resolveRefs(operation.getParameters()));
    }

    private List<Parameter> resolveRefs(List<Parameter> parameters) {
        if (parameters == null)
            return null;

        return parameters.stream().map(this::resolveParamIfNeeded).toList();
    }

    private Parameter resolveParamIfNeeded(Parameter p ) {
        if (p.get$ref() != null)
            return resolveReferencedParameter(p);
        return p;
    }

    public Parameter resolveReferencedParameter(Parameter p) {
        return api.getComponents().getParameters().get(getComponentLocalNameFromRef(p.get$ref()));
    }

    private ValidationErrors validateHeaderParameter(ValidationContext ctx, Map<String, String> hparams, Parameter param) {
        ValidationErrors errors = new ValidationErrors();
        String value = hparams.get(param.getName());

        if (value != null) {
            errors.add(new SchemaValidator(api, param.getSchema()).validate(ctx
                            .statusCode(400)
                            .entity(param.getName())
                            .entityType(HEADER_PARAMETER)
                    , value));
        } else if (param.getRequired()) {
            errors.add(ctx, format("Missing required header parameter %s.", param.getName()));
        }
        return errors;
    }

}
