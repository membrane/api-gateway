/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.net.*;
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
        if (operation == null)
            throw new MethodNotAllowException();

        isMethodDeclaredInAPI(ctx, req.getMethod(), operation);

        // If there is no response we have to validate the request
        if (response == null) {
            validatePathParameters(ctx, req, operation.getParameters());

            errors.add(new QueryParameterValidator(api,pathItem).validateQueryParameters(ctx, req, operation));

            return errors.add(new RequestBodyValidator(api).validateRequestBody(ctx.validatedEntityType(BODY).validatedEntity("REQUEST"), operation, req));
        } else {
            return errors.add(new ResponseBodyValidator(api).validateResponseBody(ctx.validatedEntityType(BODY).validatedEntity("RESPONSE"), response, operation));
        }
    }

    private Operation getOperation(String method, PathItem pi) throws MethodNotAllowException {
        return switch (method.toUpperCase()) {
            case "GET" -> pi.getGet();
            case "POST" -> pi.getPost();
            case "PUT" -> pi.getPut();
            case "DELETE" -> pi.getDelete();
            case "PATCH" -> pi.getPatch();
            default -> throw new MethodNotAllowException();
        };
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
