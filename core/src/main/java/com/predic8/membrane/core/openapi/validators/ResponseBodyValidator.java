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
import io.swagger.v3.oas.models.responses.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static io.swagger.v3.oas.models.responses.ApiResponses.DEFAULT;
import static java.lang.String.*;

public class ResponseBodyValidator extends AbstractBodyValidator<Response<?>> {

    @Override
    public int getDefaultStatusCode() {
        return 500;
    }

    @Override
    public String getMessageName() {
        return "Response";
    }

    public ResponseBodyValidator(OpenAPI api) {
        super(api);
    }

    @Override
    protected int getStatusCodeForWrongMediaType() {
        return 500;
    }

    public ValidationErrors validate(ValidationContext ctx, Response<?> response, Operation operation) {

        if (operation.getResponses() == null) {
            throw new RuntimeException("An operation should always have at least one response declared. Please check the OpenAPI document.");
        }

        ctx = ctx.statusCode(500); // Body is in the Response from the server. So it must be a 5XX code

        ValidationErrors errors = new ValidationErrors();
        Pair<Boolean, ValidationErrors> vrr = findExactMatchingResponseByStatusCodeAndValidate(ctx, operation, response);
        boolean foundMatchingResponse = vrr.first();
        errors.add(vrr.second());

        // Maybe a wildcard like 2XX, 3XX could match
        if (!foundMatchingResponse) {
            Pair<Boolean, ValidationErrors> vr = matchStatuscodeWildcardsAndValidate(ctx, operation, response);
            foundMatchingResponse = vr.first();
            errors.add(vr.second());
        }

        // Maybe there is a default-Response
        if (!foundMatchingResponse && operation.getResponses().get(DEFAULT) != null) {
            foundMatchingResponse = true;
            errors.add(validateBody(ctx, operation.getResponses().get(DEFAULT), response));
        }


        if (!foundMatchingResponse) {
            errors.add(ctx, format("Server returned a status code of %d but allowed are only %s",
                    response.getStatusCode(), joinByComma(operation.getResponses().keySet())));
        }

        return errors;
    }

    private Pair<Boolean, ValidationErrors> matchStatuscodeWildcardsAndValidate(ValidationContext ctx, Operation operation, Response<?> response) {
        AtomicBoolean foundMatchingResponse = new AtomicBoolean();
        ValidationErrors errors = new ValidationErrors();
        operation.getResponses().forEach((statusCode, responseSpec) -> {

            // No wildcard like 2XX
            if (!statusCode.endsWith("XX"))
                return;

            if (!response.matchesWildcard(statusCode))
                return;

            foundMatchingResponse.set(true);
            errors.add(validateBody(ctx, responseSpec, response));
        });
        return new Pair<>(foundMatchingResponse.get(), errors);
    }

    private Pair<Boolean, ValidationErrors> findExactMatchingResponseByStatusCodeAndValidate(ValidationContext ctx, Operation operation, Response<?> response) {
        ValidationErrors errors = new ValidationErrors();
        AtomicBoolean foundMatchingResponse = new AtomicBoolean();
        operation.getResponses().forEach((statusCode, responseSpec) -> {
            if (!response.sameStatusCode(statusCode))
                return;
            foundMatchingResponse.set(true);
            errors.add(new ResponseHeaderValidator(api, responseSpec).validateHeaders(ctx, response));
            errors.add(validateBody(ctx, responseSpec, response));
        });
        return new Pair<>(foundMatchingResponse.get(), errors);
    }

    private ValidationErrors validateBody(ValidationContext ctx, ApiResponse responseSpec, Response<?> response) {
        if (responseSpec.getContent() != null) {
            return validateBodyInternal(ctx, response, responseSpec.getContent());
        }

        String ref = responseSpec.get$ref();
        if (ref != null) {
            return validateBodyInternal(ctx, response, api.getComponents().getResponses().get(getComponentLocalNameFromRef(ref)).getContent());
        }

        if (response.hasBody()) {
            return ValidationErrors.create(ctx, "Response shouldn't have a body. There is no content described in the API specification.");
        }
        return new ValidationErrors();
    }
}