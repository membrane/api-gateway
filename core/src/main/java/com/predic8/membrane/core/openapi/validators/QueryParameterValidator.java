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
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;

public class QueryParameterValidator extends AbstractParameterValidator{

    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    ValidationErrors validateQueryParameters(ValidationContext ctx, Request request, Operation operation)  {
        Map<String, String> queryParams = getQueryParams(getQueryString(request));
        return getParametersOfType(operation, QueryParameter.class)
                .map(param -> getErrors(ctx, queryParams, param))
                .reduce(ValidationErrors::add)
                .orElse(new ValidationErrors())
                .add(validateAdditionalQueryParameters(ctx, queryParams, operation, api));
    }

    private ValidationErrors getErrors(ValidationContext ctx, Map<String, String> queryParams, Parameter param) {
        ValidationErrors err = getValidationErrors(ctx, queryParams, param, QUERY_PARAMETER);
        queryParams.remove(param.getName());
        return err;
    }

    private static String getQueryString(Request request) {
        return (new URIFactory().createWithoutException(request.getPath())).getQuery();
    }

    private Map<String, String> getQueryParams(String query) {
        if (query != null)
            return URLParamUtil.parseQueryString(query, ERROR);
        return new HashMap<>();
    }

   /* private ValidationError validateAdditionalQueryParameters(ValidationContext ctx, Map<String, String> qparams, Operation op) {
        if (!qparams.isEmpty()) {
            return new ValidationError(ctx.entityType(QUERY_PARAMETER), "There are query parameters that are not supported by the API: " + qparams.keySet());
        }
        return null;
    }*/

    private ValidationError validateAdditionalQueryParameters(ValidationContext ctx, Map<String, String> qparams, Operation op, OpenAPI openAPI) {
        boolean isGlobalPresent = openAPI.getSecurity() != null && !openAPI.getSecurity().isEmpty();
        boolean isGlobalSatisfied = isGlobalPresent && checkSecurityRequirements(openAPI.getSecurity(), qparams, openAPI);

        if (isGlobalPresent && !isGlobalSatisfied) {
            return new ValidationError(ctx.entityType(QUERY_PARAMETER), "Query parameters do not satisfy global security requirements: " + qparams.keySet());
        }

        boolean isLocalPresent = op.getSecurity() != null && !op.getSecurity().isEmpty();
        boolean isLocalSatisfied = isLocalPresent && checkSecurityRequirements(op.getSecurity(), qparams, openAPI);
        if (isLocalPresent && !isLocalSatisfied) {
            return new ValidationError(ctx.entityType(QUERY_PARAMETER), "Query parameters do not satisfy operation-level security requirements: " + qparams.keySet());
        }

        if (!qparams.isEmpty()) {
            return new ValidationError(ctx.entityType(QUERY_PARAMETER), "There are query parameters that are not supported by the API: " + qparams.keySet());
        }

        return null;
    }

    boolean checkSecurityRequirements(List<SecurityRequirement> securityRequirements, Map<String, String> qparams, OpenAPI api) {
        return securityRequirements.stream().anyMatch(req -> {
            for (String key : req.keySet()) {
                SecurityScheme scheme = api.getComponents().getSecuritySchemes().get(key);
                if (scheme != null && scheme.getIn() != null && scheme.getIn().toString().equals("query")) {
                    if (!validateParams(scheme, qparams)) {
                        return false;
                    }
                    qparams.remove(scheme.getName());
                }
            }
            return true;
        });
    }

    boolean validateParams(SecurityScheme s, Map<String, String> qparams) {
        if (s.getName() != null) return qparams.containsKey(s.getName());
        return false;
    }
}
