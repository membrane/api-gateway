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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
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

import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.OBJECT;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.*;
import static java.net.URLDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

public class QueryParameterValidator extends AbstractParameterValidator {

    private static final Pattern QUERY_PARAMS_PATTERN = Pattern.compile("([^=]*)=?(.*)");

    /**
     * @param api      OpenAPI
     * @param pathItem Needed to get parameter declaration at path level
     */
    public QueryParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    /**
     * Not only GET can have query parameters! Strange, but true!
     * e.g. POST /users?dryRun=true
     */
    ValidationErrors validateQueryParameters(ValidationContext validationContext, Request<?> request, Operation operation) {

        final var ctx = validationContext.entityType(QUERY_PARAMETER).statusCode(400);

        Set<String> required = getRequiredQueryParameters(operation);

        ValidationErrors errors = new ValidationErrors();

        Map<String, List<String>> parameterMap = getParameterMapFromQuery(getQueryString(request));

        Set<String> fields = new HashSet<>(parameterMap.keySet());

        // report all missing required params once
        var missingRequired = required.stream()
                .filter(r -> !parameterMap.containsKey(r))
                .collect(toCollection(LinkedHashSet::new));
        if (!missingRequired.isEmpty()) {
            errors.add(ctx, "Required query parameter(s) '%s' missing.".formatted(join(missingRequired)));
        }

        getAllQueryParameters(operation).forEach(p -> {
            Schema schema = OpenAPIUtil.resolveSchema(api, p);
            if (!parameterMap.containsKey(p.getName()))
                return;
            errors.add(validate(ctx, p.getName(), parameterMap, schema, p));
            fields.remove(p.getName());
        });


        var validFieldNamesFromObjects = getPossibleObjectPropertiesNamesForOperation(operation);
        fields.forEach(f -> {
            if (validFieldNamesFromObjects.contains(f)) {
                return;
            }
            errors.add(ctx, "Query parameter '%s' is invalid!".formatted(f));
        });
        return errors;
    }

    private ValidationErrors validate(ValidationContext ctx, String parameterName, Map<String, List<String>> v, Schema schema, Parameter parameter) {
        ValidationErrors errors = new ValidationErrors();
        ValidationErrors localErrors = new ValidationErrors();
        AtomicBoolean validated = new AtomicBoolean();
        // Maybe there is no schema for that parameter
        if (schema == null) {
            return errors;
        }
        Set<String> types = schema.getTypes(); // Try all e.g. type: [array, null]
        // Maybe there is no type
        if (types == null) {
            return errors;
        }
        types.forEach(type -> {
            ParameterParser ap = AbstractParameterParser.instance(api, type, parameter);
            ap.setValues(v);
            try {
                ValidationErrors err = new SchemaValidator(api, schema).validate(ctx
                                .entity(parameterName)
                        , ap.getJson());
                if (err.hasErrors()) {
                    localErrors.add(err);
                    return;
                }
                validated.set(true); // Validation against one type succeeded
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse/validate query parameter '%s': %s".formatted(parameterName, e.getMessage()), e);
            }
        });
        if (!validated.get()) {
            if (types.size() == 1) {
                return errors.add(localErrors);
            }
            return errors.add(ctx, "Validation of query parameter '%s' failed against all types(%s). Details are: %s".formatted(parameterName, types, localErrors));
        }
        return errors;
    }

    ValidationErrors validateAdditionalQueryParameters(ValidationContext ctx, Map<String, JsonNode> qparams, OpenAPI api) {
        Set<String> allowList = new HashSet<>(securitySchemeApiKeyQueryParamNames(api));
        Set<String> unsupported = qparams.keySet().stream()
                .filter(k -> !allowList.contains(k))
                .collect(toCollection(LinkedHashSet::new));
        if (!unsupported.isEmpty()) {
            return ValidationErrors.error(ctx.entityType(QUERY_PARAMETER),
                    "There are query parameters that are not supported by the API: " + join(unsupported));
        }

        return ValidationErrors.empty();
    }

    public List<String> securitySchemeApiKeyQueryParamNames(OpenAPI api) {
        if (api.getComponents() == null || api.getComponents().getSecuritySchemes() == null)
            return emptyList();

        return api.getComponents().getSecuritySchemes().values().stream()
                .filter(scheme -> scheme != null && APIKEY.equals(scheme.getType()) && QUERY.equals(scheme.getIn()))
                .map(SecurityScheme::getName)
                .toList();
    }

    private static @NotNull Map<String, List<String>> getParameterMapFromQuery(String query) {
        Map<String, List<String>> parameterMap = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return parameterMap;
        }
        for (String p : query.split("&")) {
            Matcher m = QUERY_PARAMS_PATTERN.matcher(p);
            if (m.matches()) {
                String key = decode(m.group(1), UTF_8); // Key can here be decoded
                String value = m.group(2); // Do not decode here cause it has to be done after array or object splitting
                List<String> ab = parameterMap.computeIfAbsent(key, k -> new ArrayList<>());
                ab.add(value);
            }
        }
        return parameterMap;
    }

    /**
     * Lookup query parameter in the operation and in the path.
     *
     * @param operation
     * @param name
     * @return
     */
    Parameter getQueryParameter(Operation operation, String name) {
        return getAllQueryParameters(operation).stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Get all query parameters declared in the operation and in the path.
     *
     * @param operation
     * @return
     */
    Set<Parameter> getAllQueryParameters(Operation operation) {
        return getAllParameter(operation).stream().filter(p -> p instanceof QueryParameter).collect(toSet());
    }

    /**
     * Needed to get the possible properties from objects and to not report them, if they are not declared as separate parameter
     *
     * @param operation
     * @return
     */
    List<String> getPossibleObjectPropertiesNamesForOperation(Operation operation) {
        var parameters = getAllQueryParameters(operation);

        List<String> names = new ArrayList<>(parameters.size());
        parameters.forEach(p -> {
            var schema = OpenAPIUtil.resolveSchema(api, p);
            if (schema == null)
                return;
            if (isObjectType(schema) && schema.getProperties() != null) {
                schema.getProperties().forEach((name, ignored) -> names.add(name));
            }
        });
        return names;
    }

    private static boolean isObjectType(Schema<?> schema) {
        Set<String> types = schema.getTypes();
        return types != null && types.contains(OBJECT) || OBJECT.equals(schema.getType());
    }


    @NotNull Set<String> getRequiredQueryParameters(Operation operation) {
        Set<Parameter> parameters = getAllQueryParameters(operation);
        if (parameters == null) {
            return emptySet();
        }
        return parameters.stream()
                .filter(Parameter::getRequired).map(Parameter::getName).collect(toSet());
    }

    static String getQueryString(Request<?> request) {
        return (new URIFactory().createWithoutException(request.getPath())).getRawQuery();
    }
}