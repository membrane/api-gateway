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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.parameters.*;
import com.predic8.membrane.core.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.security.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.util.CollectionsUtil.join;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

public class QueryParameterValidator extends AbstractParameterValidator {

    private static final Pattern QUERY_PARAMS_PATTERN = Pattern.compile("([^=]*)=?(.*)");

    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    /**
     * Not only GET can have query parameters! Strange, but true!
     * e.g. POST /users?dryRun=true
     *
     */
    ValidationErrors validateQueryParameters(ValidationContext ctx, Request<?> request, PathItem pathItem, Operation operation) {

        Set<String> required = getRequiredQueryParameters( pathItem,operation);

        ValidationErrors errors = new ValidationErrors();

        Map<String, Set<String>> parameterMap = getParameterMapFromQuery(getQueryString(request));

        parameterMap.forEach((parameterName, v) -> {
            required.remove(parameterName);

            Parameter parameter = getQueryParameter(pathItem,operation, parameterName);
            if (parameter == null) {
                errors.add(ctx.entityType(QUERY_PARAMETER), "Query parameter '" + parameterName + "' is invalid!");
                return;
            }
            Schema schema = getSchema(parameter);
            if (schema == null) {
                // The query parameter is declared but there is no schema
                return;
            }
            errors.add(validate(ctx, parameterName, v, schema, parameter));
        });

        if (!required.isEmpty()) {
            errors.add(ctx.entityType(QUERY_PARAMETER), "Required query parameter(s) '%s' missing.".formatted( join(required)));
        }
        return errors;
    }

    private ValidationErrors validate(ValidationContext ctx, String parameterName, Set<String> v, Schema schema, Parameter parameter) {
        ValidationErrors errors = new ValidationErrors();
        ValidationErrors localErrors = new ValidationErrors();
        AtomicBoolean validated = new AtomicBoolean();
        // Try all e.g. type: [array, null]
        Set<String> types = schema.getTypes();
        types.forEach(type -> {
            AbstractParameter ap = AbstractParameter.instance(schema, parameter).iterator().next();
            ap.addAllValues(v);
            try {
                ValidationErrors err = new SchemaValidator(api, schema).validate(ctx
                                .statusCode(400)
                                .entity(parameterName)
                                .entityType(QUERY_PARAMETER)
                        , ap.getJson());
                if (err.hasErrors()) {
                    localErrors.add(err);
                } else {
                    validated.set(true); // Validation against one type succeeded
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e); // ToDO test foo=ff'ff
            }
        });
        if (!validated.get()) {
            if (types.size() == 1) {
                return errors.add(localErrors);
            }
            return errors.add(ctx, "Validation of query parameter '%s' failed against all types(%s). Details are: %s".formatted( parameterName, types, localErrors ));

        }
        return errors;
    }

    static @NotNull Set<String> getRequiredQueryParameters(PathItem pathItem, Operation operation) {
        Set<Parameter> parameters = getQueryParameters(pathItem,operation);
        if (parameters == null) {
            return emptySet();
        }
        return parameters.stream()
                .filter(p -> p.getRequired()).map(Parameter::getName).collect(toSet());
    }

    private static String getQueryString(Request<?> request) {
        return (new URIFactory().createWithoutException(request.getPath())).getQuery();
    }

    ValidationErrors validateAdditionalQueryParameters(ValidationContext ctx, Map<String, JsonNode> qparams, OpenAPI api) {
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

    private static @NotNull Map<String, Set<String>> getParameterMapFromQuery(String query) {
        Map<String, Set<String>> parameterMap = new HashMap<>();
        if (query == null) {
            return parameterMap;
        }
        for (String p : query.split("&")) {
            Matcher m = QUERY_PARAMS_PATTERN.matcher(p);
            if (m.matches()) {
                String key = decode(m.group(1), UTF_8);
                String value = decode(m.group(2), UTF_8);
                Set<String> ab = parameterMap.computeIfAbsent(key, k -> new HashSet<>());
                ab.add(value);
            }
        }
        return parameterMap;
    }

    // TODO
    static Parameter getQueryParameter(PathItem pathItem, Operation operation, String name) {
        return getQueryParameters(pathItem, operation).stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    static Set<Parameter> getQueryParameters(PathItem pathItem,Operation operation) {
        Set<Parameter> parameters = new HashSet<>();
        if (operation.getParameters() != null) {
            parameters.addAll(operation.getParameters());
        }
        if (pathItem.getParameters() != null) {
            parameters.addAll(pathItem.getParameters());
        }
        return parameters.stream().filter(p -> p instanceof QueryParameter).collect(toSet());
    }

}