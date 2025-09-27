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
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.parameters.*;

import java.util.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.*;

public class RequestHeaderParameterValidator extends AbstractParameterValidator{

    public RequestHeaderParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    ValidationErrors validateHeaderParameters(ValidationContext ctx, Request<?> request, Operation operation)  {

        return getParametersOfType(operation, HeaderParameter.class)
                .map(param -> {

                    var headers = request.getHeaders();

                    String value = null;

                    if (headers != null) {
                        value = request.getHeaders().get(param.getName());
                    }

                    if (value != null) {
                        if (param.getSchema() != null) {
                            return new SchemaValidator(api, param.getSchema()).validate(ctx
                                            .statusCode(400)
                                            .entity(param.getName())
                                            .entityType(HEADER_PARAMETER)
                                    , value);
                        }
                    } else if (param.getRequired()) {
                        return ValidationErrors.error(ctx.statusCode(400)
                                .entity(param.getName()).entityType(HEADER_PARAMETER), format("Missing required header %s.", param.getName()));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .reduce(ValidationErrors::add)
                .orElse(new ValidationErrors());
    }
}