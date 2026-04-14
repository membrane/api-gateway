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

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import com.predic8.membrane.core.openapi.util.MethodNotAllowException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;

import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.SECURITY;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor.shouldValidate;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.BODY;

public class OperationValidator {

    final OpenAPI api;
    final PathItem pathItem;
    final Operation operation;

    private OperationValidator(OpenAPI api, PathItem pathItem, Operation operation) {
        this.api = api;
        this.pathItem = pathItem;
        this.operation = operation;
    }

    public static OperationValidator create(OpenAPI api, String method, PathItem pathItem) throws MethodNotAllowException {
        var operation = getOperation(method, pathItem);
        if (operation == null)
            throw new MethodNotAllowException();
        return new OperationValidator(api, pathItem, operation);
    }

    public ValidationErrors validateRequest(ValidationContext ctx, Request<?> req) {
        var errors = new ValidationErrors();
        errors.add(validatePathParameters(ctx, req, operation.getParameters()));
        errors.add(new QueryParameterValidator(api, pathItem).validate(ctx, req, operation));
        errors.add(new RequestHeaderParameterValidator(api, pathItem).validateHeaderParameters(ctx, req, operation));
        if (shouldValidate(api, SECURITY))
            errors.add(new SecurityValidator(api).validateSecurity(ctx, req, operation));
        errors.add(new RequestBodyValidator(api).validate(ctx.entityType(BODY).entity("REQUEST"), req, operation));
        return errors.add(validatePathParameters(ctx, req, pathItem.getParameters()));
    }

    public ValidationErrors validateResponse(ValidationContext ctx, Response<?> response) {
        return new ResponseBodyValidator(api).validate(ctx.entityType(BODY).entity("RESPONSE"), response, operation);
    }

    private ValidationErrors validatePathParameters(ValidationContext ctx, Request<?> req, List<Parameter> schemaParameters) {
        return new PathParametersValidator(api).validatePathParameters(ctx, req, schemaParameters);
    }

    private static Operation getOperation(String method, PathItem pi) throws MethodNotAllowException {
        return switch (method.toUpperCase()) {
            case "GET" -> pi.getGet();
            case "HEAD" -> pi.getHead();
            case "OPTIONS" -> pi.getOptions();
            case "POST" -> pi.getPost();
            case "PUT" -> pi.getPut();
            case "DELETE" -> pi.getDelete();
            case "PATCH" -> pi.getPatch();
            case "TRACE" -> pi.getTrace();
            default -> throw new MethodNotAllowException();
        };
    }
}
