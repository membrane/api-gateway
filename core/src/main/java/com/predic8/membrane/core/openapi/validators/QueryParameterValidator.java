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

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.hasObjectType;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.isExplode;
import static com.predic8.membrane.core.openapi.validators.JsonSchemaValidator.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.error;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.In.*;
import static io.swagger.v3.oas.models.security.SecurityScheme.Type.*;
import static java.lang.Boolean.TRUE;
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
    ValidationErrors validate(ValidationContext validationContext, Request<?> request, Operation operation) {

        final var ctx = validationContext.entityType(QUERY_PARAMETER).statusCode(400);

        var errors = new ValidationErrors();
        Map<String, List<String>> parameterMap = null;
        try {
            parameterMap = getParameterMapFromQuery(getQueryString(request));
        } catch (Exception e) {
            return errors.add(ctx, "Invalid query string: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
        var fields = new LinkedHashSet<>(parameterMap.keySet());

        errors.add(checkMissingRequiredFields(operation, parameterMap, ctx));

        Map<String, List<String>> finalParameterMap = parameterMap;
        getAllQueryParameters(operation).forEach(p -> {
            errors.add(validateParameter(ctx, p.getName(), finalParameterMap, p));
            fields.remove(p.getName());
        });

        // At least one parameter with type object and explode collects all unknown query parameters
        if (hasExplodedObjectWithAdditionalProperties(operation)) {
            return errors;
        }

        var validFieldNamesFromObjects = getPossibleObjectPropertiesNamesForOperation(operation);
        fields.forEach(f -> {
            if (validFieldNamesFromObjects.contains(f)) {
                return;
            }
            errors.add(ctx.parameter(f), "Unknown query parameter '%s' is invalid!".formatted(f));
        });

        return errors;
    }

    /**
     * Report all missing required params once
     */
    private ValidationErrors checkMissingRequiredFields(Operation operation, Map<String, List<String>> parameterMap, ValidationContext ctx) {
        var missingRequired = getMissingRequiredFields(getRequiredQueryParameters(operation), parameterMap);
        if (missingRequired.isEmpty())
            return null;
        return error(ctx, "Required query parameter(s) '%s' missing.".formatted(join(missingRequired)));
    }

    private static @NotNull LinkedHashSet<String> getMissingRequiredFields(Set<String> required, Map<String, List<String>> parameterMap) {
        return required.stream()
                .filter(r -> !parameterMap.containsKey(r))
                .collect(toCollection(LinkedHashSet::new));
    }

    private boolean hasExplodedObjectWithAdditionalProperties(Operation operation) {
        for (Parameter p : getAllQueryParameters(operation)) {
            Schema<?> s = OpenAPIUtil.resolveSchema(api, p);
            if (s == null) continue;
            if (!hasObjectType(s)) continue;
            if (!isExplode(p)) continue;
            Object additional = s.getAdditionalProperties();
            if (TRUE.equals(additional) || (additional instanceof Schema)) {
                return true;
            }
        }
        return false;
    }

    private ValidationErrors validateParameter(ValidationContext ctx, String parameterName, Map<String, List<String>> v, Parameter parameter) {
        var schema = OpenAPIUtil.resolveSchema(api, parameter);
        var errors = new ValidationErrors();
        var localErrors = new ValidationErrors();
        AtomicBoolean validated = new AtomicBoolean();
        // Maybe there is no schema for that parameter
        if (schema == null) {
            return errors;
        }
        getTypes(schema).forEach(type -> {

            // Do not try to validate parameters that are not in the query string unless it is an object.
            // Maybe the query parameter is a property from that object
            if (v.get(parameterName) == null && !OBJECT.equals(type)) {
                validated.set(true);
                return;
            }

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
            if (getTypes(schema).size() == 1) {
                return errors.add(localErrors);
            }
            return errors.add(ctx, "Validation of query parameter '%s' failed against all types(%s). Details are: %s".formatted(parameterName, getTypes(schema), localErrors));
        }
        return errors;
    }

    private static @NotNull Set<String> getTypes(Schema<?> schema) {
        var types = schema.getTypes(); // Try all e.g. type: [array, null]
        if (types == null || types.isEmpty()) {
            var t = schema.getType();
            return t != null ? Set.of(t) : Set.of("string");
        }
        return types;
    }

    ValidationErrors validateAdditionalQueryParameters(ValidationContext ctx, Map<String, JsonNode> qparams, OpenAPI api) {
        var allowList = new HashSet<>(securitySchemeApiKeyQueryParamNames(api));
        var unsupported = qparams.keySet().stream()
                .filter(k -> !allowList.contains(k))
                .collect(toCollection(LinkedHashSet::new));
        if (!unsupported.isEmpty()) {
            return error(ctx.entityType(QUERY_PARAMETER),
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
        Map<String, List<String>> parameterMap = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return parameterMap;
        }
        for (String p : query.split("&")) {
            Matcher m = QUERY_PARAMS_PATTERN.matcher(p);
            if (m.matches()) {
                var key = decode(m.group(1), UTF_8); // Key can here be decoded
                if (key.isEmpty()) continue; // ignore stray separators
                var value = m.group(2); // Do not decode here cause it has to be done after array or object splitting
                var ab = parameterMap.computeIfAbsent(key, k -> new ArrayList<>());
                ab.add(value);
            }
        }
        return parameterMap;
    }

    /**
     * Lookup query parameter in the operation and in the path.
     */
    Parameter getQueryParameter(Operation operation, String name) {
        return getAllQueryParameters(operation).stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Get all query parameters declared in the operation and in the path.
     */
    Set<Parameter> getAllQueryParameters(Operation operation) {
        return getAllParameter(operation).stream().filter(p -> p instanceof QueryParameter).collect(toCollection(LinkedHashSet::new));
    }

    /**
     * Needed to get the possible properties from objects and to not report them, if they are not declared as separate parameter
     */
    List<String> getPossibleObjectPropertiesNamesForOperation(Operation operation) {
        var parameters = getAllQueryParameters(operation);

        List<String> names = new ArrayList<>(parameters.size());
        parameters.forEach(p -> {
            var schema = OpenAPIUtil.resolveSchema(api, p);
            if (schema == null)
                return;
            if (hasObjectType(schema) && schema.getProperties() != null) {
                schema.getProperties().forEach((name, ignored) -> names.add(name));
            }
        });
        return names;
    }

    @NotNull Set<String> getRequiredQueryParameters(Operation operation) {
        var parameters = getAllQueryParameters(operation);
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