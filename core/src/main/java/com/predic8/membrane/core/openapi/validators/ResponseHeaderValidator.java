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
import io.swagger.v3.oas.models.headers.*;
import io.swagger.v3.oas.models.responses.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.HEADER_PARAMETER;
import static java.lang.String.format;

public class ResponseHeaderValidator {

    final OpenAPI api;
    final ApiResponse apiResponse;

    public ResponseHeaderValidator(OpenAPI api, ApiResponse apiResponse) {
        this.api = api;
        this.apiResponse = apiResponse;
    }

    public ValidationErrors validateHeaders(ValidationContext ctx, Message message) {

        if(resolveResponseRef(apiResponse).getHeaders() == null)
            return null;

        ValidationErrors errors = new ValidationErrors();

        resolveResponseRef(apiResponse).getHeaders().forEach((s, headerSpec) -> validateHeader(ctx, message, s, headerSpec, errors));

        return errors;
    }

    private void validateHeader(ValidationContext ctx, Message response, String s, Header headerSpecUnresolved, ValidationErrors errors) {
        Header headerSpec = resolveHeaderRef(headerSpecUnresolved);

        var headers = response.getHeaders();

        String value = null;

        if (headers != null) {
            value = response.getHeaders().get(s);
        }

        if (value != null) {
            if (headerSpec.getSchema() != null) {
                errors.add(new SchemaValidator(api, headerSpec.getSchema()).validate(ctx
                                .statusCode(500)
                                .entity(s)
                                .entityType(HEADER_PARAMETER)
                        , value));
            }
        } else if (headerSpec.getRequired() != null && headerSpec.getRequired()) {
            errors.add(ctx.entity(s).entityType(HEADER_PARAMETER), format("Missing required header %s.", s));
        }
    }

    private Header resolveHeaderRef(Header h) {
        if (h.get$ref() != null) {
            return api.getComponents().getHeaders().get(getComponentLocalNameFromRef(h.get$ref()));
        }
        return h;
    }

    private ApiResponse resolveResponseRef(ApiResponse p) {
        if (p.get$ref() != null) {
            return api.getComponents().getResponses().get(getComponentLocalNameFromRef(p.get$ref()));
        }
        return p;
    }
}
