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
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static java.lang.String.format;

public class QueryParameterValidator {

    OpenAPI api;
    PathItem pathItem;

    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    ValidationErrors validateQueryParameters(ValidationContext ctx, Request request, Operation operation) {

        ValidationErrors errors = new ValidationErrors();
        Map<String, String> qparams = request.getQueryParams();

        getAllParameterSchemas(operation).forEach(param -> {
            if (!(param instanceof QueryParameter)) {
                return;
            }
            errors.add(validateQueryParameter(ctx.validatedEntity(param.getName()).validatedEntityType(QUERY_PARAMETER), qparams, param));
            qparams.remove(param.getName()); // Delete param so there should't be any parameter left
        });

        errors.add(checkForAdditionalQueryParameters(ctx, qparams));

        return errors;
    }

    private List<Parameter> getAllParameterSchemas(Operation operation) {
        return concat(pathItem.getParameters(), operation.getParameters());
    }

    private static List<Parameter> concat(List<Parameter> l1, List<Parameter> l2) {
        if (l1 == null) {
            if (l2 != null)
                return l2;
            else
                return new ArrayList<>();
        }
        if (l2!=null)
            l1.addAll(l2);
        return l1;
    }

    private ValidationErrors validateQueryParameter(ValidationContext ctx, Map<String, String> qparams, Parameter param) {
        ValidationErrors errors = new ValidationErrors();
        String value = qparams.get(param.getName());

        if (value != null) {
            errors.add(new SchemaValidator(api, param.getSchema()).validate(ctx
                            .statusCode(400)
                            .validatedEntity(param.getName())
                            .validatedEntityType(QUERY_PARAMETER)
                    , value));
        } else if (param.getRequired()) {
            errors.add(ctx, format("Missing required query parameter %s.", param.getName()));
        }
        return errors;
    }

    private ValidationError checkForAdditionalQueryParameters(ValidationContext ctx, Map<String, String> qparams) {
        if (qparams.size() > 0) {
            return new ValidationError(ctx.validatedEntityType(QUERY_PARAMETER), "There are query parameters that are not supported by the API: " + qparams.keySet());
        }
        return null;
    }
}
