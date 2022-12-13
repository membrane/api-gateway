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

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH_PARAMETER;

public class PathParametersValidator {

    OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public PathParametersValidator(OpenAPI api) {
        this.api = api;
    }

    public ValidationErrors validatePathParameters(ValidationContext ctx, Request req, List<Parameter> schemaParameters) {

        if (schemaParameters == null || req.getPathParameters().size() == 0)
            return null;

        schemaParameters.stream().filter(this::isPathParameter).forEach(parameter -> {
            String value = req.getPathParameters().get(parameter.getName());
            if (value == null) {
                throw new RuntimeException("Should not happen!");
            }
            errors.add(new SchemaValidator(api, parameter.getSchema()).validate(ctx.validatedEntityType(PATH_PARAMETER)
                    .validatedEntity(parameter.getName())
                    .path(req.getPath())
                    .statusCode(400), value));
        });
        return errors;
    }

    private boolean isPathParameter(Parameter p) {
        return p instanceof PathParameter;
    }
}
