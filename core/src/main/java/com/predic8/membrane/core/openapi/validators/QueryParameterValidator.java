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
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.QUERY;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY;
import static java.util.Collections.emptyList;

public class QueryParameterValidator extends AbstractParameterValidator{

    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    ValidationErrors validateQueryParameters(ValidationContext ctx, Request<?> request, Operation operation)  {
        Map<String, String> queryParams = getQueryParams(getQueryString(request));
        return getParametersOfType(operation, QueryParameter.class)
                .map(param -> getErrors(ctx, queryParams, param))
                .reduce(ValidationErrors::add)
                .orElse(new ValidationErrors())
                .add(validateAdditionalQueryParameters(ctx, queryParams, api));
    }

    private ValidationErrors getErrors(ValidationContext ctx, Map<String, String> queryParams, Parameter param) {
        ValidationErrors err = getValidationErrors(ctx, queryParams, param, QUERY_PARAMETER);
        queryParams.remove(param.getName());
        return err;
    }

    private static String getQueryString(Request<?> request) {
        return (new URIFactory().createWithoutException(request.getPath())).getQuery();
    }

    private Map<String, String> getQueryParams(String query) {
        if (query != null)
            return URLParamUtil.parseQueryString(query, ERROR);
        return new HashMap<>();
    }

    ValidationErrors validateAdditionalQueryParameters(ValidationContext ctx, Map<String, String> qparams, OpenAPI api) {
        securitySchemeApiKeyQueryParamNames(api).forEach(qparams::remove);

        if (!qparams.isEmpty()) {
            return ValidationErrors.create(ctx.entityType(QUERY_PARAMETER), "There are query parameters that are not supported by the API: " + qparams.keySet());
        }

        return ValidationErrors.empty();
    }

    public List<String> securitySchemeApiKeyQueryParamNames(OpenAPI api) {
        if (api.getComponents() == null || api.getComponents().getSecuritySchemes() == null)
            return emptyList();

        return api.getComponents().getSecuritySchemes().values().stream()
                .filter(scheme -> scheme != null && scheme.getType() != null && scheme.getType().equals(APIKEY) && scheme.getIn().equals(QUERY))
                .map(SecurityScheme::getName)
                .toList();
    }
}