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
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static java.lang.String.*;

public class QueryParameterValidator {

    OpenAPI api;
    PathItem pathItem;

    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    ValidationErrors validateQueryParameters(ValidationContext ctx, Request request, Operation operation)  {

        ValidationErrors errors = new ValidationErrors();

        // TODO
        // Router?
        String query = (new URIFactory().createWithoutException(request.getPath())).getQuery();
        Map<String, String> qparams = getQueryParams(query);

        getAllParameterSchemas(operation).forEach(param -> {
            if (!(param instanceof QueryParameter)) {
                return;
            }
            errors.add(validateQueryParameter(ctx.entity(param.getName()).entityType(QUERY_PARAMETER), qparams, param));
            qparams.remove(param.getName()); // Delete param so there shouldn't be any parameter left
        });

        errors.add(checkForAdditionalQueryParameters(ctx, qparams));

        return errors;
    }

    private Map<String, String> getQueryParams(String query) {
        if (query != null)
            return URLParamUtil.parseQueryString(query, ERROR);
        return new HashMap<>();
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

    private ValidationErrors validateQueryParameter(ValidationContext ctx, Map<String, String> qparams, Parameter param) {
        ValidationErrors errors = new ValidationErrors();
        String value = qparams.get(param.getName());

        if (value != null) {
            errors.add(new SchemaValidator(api, param.getSchema()).validate(ctx
                            .statusCode(400)
                            .entity(param.getName())
                            .entityType(QUERY_PARAMETER)
                    , value));
        } else if (param.getRequired()) {
            errors.add(ctx, format("Missing required query parameter %s.", param.getName()));
        }
        return errors;
    }

    private ValidationError checkForAdditionalQueryParameters(ValidationContext ctx, Map<String, String> qparams) {
        if (!qparams.isEmpty()) {
            return new ValidationError(ctx.entityType(QUERY_PARAMETER), "There are query parameters that are not supported by the API: " + qparams.keySet());
        }
        return null;
    }
}
