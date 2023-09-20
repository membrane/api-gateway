package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static java.lang.String.*;

public class HeaderParameterValidator {

    OpenAPI api;
    PathItem pathItem;

    public HeaderParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    ValidationErrors validateHeaderParameters(ValidationContext ctx, Request request, Operation operation)  {
        return getHeaderParameters(operation)
                .map(param -> getValidateHeaderParameter(ctx, request, param))
                .reduce(ValidationErrors::add)
                .orElse(new ValidationErrors());
    }

    private Stream<Parameter> getHeaderParameters(Operation operation) {
        return getAllParameterSchemas(operation).stream().filter(this::isHeader);
    }

    private ValidationErrors getValidateHeaderParameter(ValidationContext ctx, Request request, Parameter param) {
        return validateHeaderParameter(getCtx(ctx, param), request.getHeaders(), param);
    }

    private boolean isHeader(Parameter p) {
        return p instanceof HeaderParameter;
    }

    private static ValidationContext getCtx(ValidationContext ctx, Parameter param) {
        return ctx.entity(param.getName())
                  .entityType(HEADER_PARAMETER)
                  .statusCode(400);
    }

    public List<Parameter> getAllParameterSchemas(Operation operation) {
        return concat(resolveRefs(pathItem.getParameters()), resolveRefs(operation.getParameters()));
    }

    private List<Parameter> resolveRefs(List<Parameter> parameters) {
        if (parameters == null)
            return null;

        return parameters.stream().map(this::resolveParamIfNeeded).toList();
    }

    // Move into AbstractParameterValidator
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