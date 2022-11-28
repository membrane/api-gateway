package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.format;

public class OperationValidator {

    OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public OperationValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validateOperation(ValidationContext ctx, Request req, Response response, PathItem pathItem) throws MethodNotAllowException {

        Operation operation = getOperation(req.getMethod(), pathItem);

        isMethodDeclaredInAPI(ctx, req.getMethod(), operation);

        // If there is no response we have to validate the request
        if (response == null) {
            validatePathParameters(ctx, req, operation.getParameters());

            QueryParameterValidator queryParameterValidator = new QueryParameterValidator(api,pathItem);
            errors.add(queryParameterValidator.validateQueryParameters(ctx, req, operation));

            RequestBodyValidator requestBodyValidator = new RequestBodyValidator(api);

            return errors.add(requestBodyValidator.validateRequestBody(ctx.validatedEntityType(BODY).validatedEntity("REQUEST"), operation, req));
        } else {
            return errors.add(new ResponseBodyValidator(api).validateResponseBody(ctx.validatedEntityType(BODY).validatedEntity("RESPONSE"), response, operation));
        }
    }

    private Operation getOperation(String method, PathItem pathItem) throws MethodNotAllowException {
        Operation op = null;
        switch (method.toUpperCase()) {
            case "GET":
                op = pathItem.getGet();
                break;
            case "POST":
                op = pathItem.getPost();
                break;
            case "PUT":
                op = pathItem.getPut();
                break;
            case "DELETE":
                op = pathItem.getDelete();
                break;
            case "PATCH":
                op = pathItem.getPatch();
                break;
            default:
                throw new MethodNotAllowException();
        }
        if (op != null)
            return op;
        throw new MethodNotAllowException();
    }

    private void isMethodDeclaredInAPI(ValidationContext ctx, String method, Object apiMethod) {
        if (apiMethod == null) {
            errors.add(new ValidationError(ctx.validatedEntity(method).validatedEntityType(METHOD).statusCode(405), format("Path %s does not support method %s", ctx.getUriTemplate(), method)));
        }
    }

    private void validatePathParameters(ValidationContext ctx, Request req, List<Parameter> schemaParameters) {

        if (schemaParameters == null || req.getPathParameters().size() == 0)
            return;

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
    }

    private boolean isPathParameter(Parameter p) {
        return p instanceof PathParameter;
    }

}
