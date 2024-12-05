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

import com.predic8.membrane.core.openapi.validators.ValidationContext.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static java.lang.String.*;

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
        return concat(pathItem.getParameters(), operation.getParameters());
    }

    boolean isTypeOf(Parameter p, Class<?> clazz) {
        return p.getClass().equals(clazz);
    }

    public ValidationErrors getValidationErrors(ValidationContext ctx, Map<String, String> parameters, Parameter param, ValidatedEntityType type) {
        return validateParameter(getCtx(ctx, param, type), parameters, param, type);
    }

    private static ValidationContext getCtx(ValidationContext ctx, Parameter param, ValidatedEntityType type) {
        return ctx.entity(param.getName())
                .entityType(type)
                .statusCode(400);
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