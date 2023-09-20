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

import com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;
import static com.predic8.membrane.core.util.CollectionsUtil.concat;
import static java.lang.String.format;

public abstract class AbstractParameterValidator {
    OpenAPI api;
    PathItem pathItem;

    public AbstractParameterValidator(OpenAPI api, PathItem pathItem) {
        this.api = api;
        this.pathItem = pathItem;
    }

    public Stream<Parameter> getParametersOfType(Operation operation, Class<?> paramClazz) {
        return getAllParameterSchemas(operation).stream().filter(p -> isTypeOf(p, paramClazz));
    }

    public List<Parameter> getAllParameterSchemas(Operation operation) {
        return concat(resolveRefs(pathItem.getParameters()), resolveRefs(operation.getParameters()));
    }

    boolean isTypeOf(Parameter p, Class<?> clazz) {
        return p.getClass().equals(clazz);
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

    public ValidationErrors getValidationErrors(ValidationContext ctx, Map<String, String> parameters, Parameter param, ValidatedEntityType type) {
        return validateParameter(getCtx(ctx, param, type), parameters, param, type);
    }

    private static ValidationContext getCtx(ValidationContext ctx, Parameter param, ValidatedEntityType type) {
        return ctx.entity(param.getName())
                .entityType(type)
                .statusCode(400);
    }

    public Parameter resolveReferencedParameter(Parameter p) {
        return api.getComponents().getParameters().get(getComponentLocalNameFromRef(p.get$ref()));
    }

    public ValidationErrors validateParameter(ValidationContext ctx, Map<String, String> params, Parameter param, ValidatedEntityType type) {
        ValidationErrors errors = new ValidationErrors();
        String value = params.get(param.getName());

        if (value != null) {
            errors.add(new SchemaValidator(api, param.getSchema()).validate(ctx
                            .statusCode(400)
                            .entity(param.getName())
                            .entityType(type)
                    , value));
        } else if (param.getRequired()) {
            errors.add(ctx, format("Missing required %s %s.", type.name, param.getName()));
        }
        return errors;
    }

}