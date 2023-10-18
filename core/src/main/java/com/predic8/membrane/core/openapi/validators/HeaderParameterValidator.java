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
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.HeaderParameter;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.HEADER_PARAMETER;

public class HeaderParameterValidator extends AbstractParameterValidator{

    public HeaderParameterValidator(OpenAPI api, PathItem pathItem) {
        super(api, pathItem);
    }

    ValidationErrors validateHeaderParameters(ValidationContext ctx, Request request, Operation operation)  {
        return getParametersOfType(operation, HeaderParameter.class)
                .map(param -> getValidationErrors(ctx, request.getHeaders(), param, HEADER_PARAMETER))
                .reduce(ValidationErrors::add)
                .orElse(new ValidationErrors());
    }
}