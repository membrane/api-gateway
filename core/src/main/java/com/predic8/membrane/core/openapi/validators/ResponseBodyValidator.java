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
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import jakarta.mail.internet.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.*;

public class ResponseBodyValidator extends AbstractBodyValidator<Response> {

    public ResponseBodyValidator(OpenAPI api) {
        super(api);
    }

    ValidationErrors validateResponseBody(ValidationContext ctx, Response response, Operation operation) {
        ValidationErrors errors = new ValidationErrors();

        if (operation.getResponses() == null)
            throw new RuntimeException("An operation should always have at least one response declared.");

        boolean foundMatchingResponse = findExactMatchingResponseByStatusCodeAndValidate(ctx, operation, response);

        // Maybe a wildcard like 2XX, 3XX could match
        if (!foundMatchingResponse) {
            foundMatchingResponse = matchStatuscodeWildcardsAndValidate(ctx, operation, response);
        }

        // Maybe there is a default-Response
        if (!foundMatchingResponse) {
            if (operation.getResponses().getDefault() != null) {
                foundMatchingResponse = true;
                errors.add(validateBody(ctx, operation.getResponses().getDefault(), response));
            }
        }

        if (!foundMatchingResponse) {
            errors.add(ctx.statusCode(500), format("Server returned a status code of %d but allowed are only %s",
                    response.getStatusCode(), joinByComma(operation.getResponses().keySet())));
        }

        return errors;
    }

    private boolean matchStatuscodeWildcardsAndValidate(ValidationContext ctx, Operation operation, Response response) {
        AtomicBoolean foundMatchingResponse = new AtomicBoolean();
        operation.getResponses().forEach((statusCode, responseSpec) -> {

            // No wildcard like 2XX
            if (!statusCode.endsWith("XX"))
                return;

            if (!response.matchesWildcard(statusCode))
                return;

            foundMatchingResponse.set(true);
            validateBody(ctx, responseSpec, response);
        });
        return foundMatchingResponse.get();
    }

    private boolean findExactMatchingResponseByStatusCodeAndValidate(ValidationContext ctx, Operation operation, Response response) {
        AtomicBoolean foundMatchingResponse = new AtomicBoolean();
        operation.getResponses().forEach((statusCode, responseSpec) -> {
            if (!response.sameStatusCode(statusCode))
                return;

            foundMatchingResponse.set(true);
            validateBody(ctx, responseSpec, response);
        });
        return foundMatchingResponse.get();
    }

    private ValidationErrors validateBody(ValidationContext ctx, ApiResponse responseSpec, Response response) {
        ValidationErrors errors = new ValidationErrors();
        if (responseSpec.getContent() != null) {
            errors.add(validateResponseBodyInternal(ctx, response, responseSpec));
        } else {
            String ref = responseSpec.get$ref();
            if (ref != null) {
                errors.add(validateResponseBodyInternal(ctx, response, api.getComponents().getResponses().get(getComponentLocalNameFromRef(ref))));
            } else {
                if (response.hasBody()) {
                    errors.add(ctx.statusCode(500), "Response shouldn't have a body. There is no content described in the API specification.");
                }
            }
        }
        return errors;
    }

    private ValidationErrors validateResponseBodyInternal(ValidationContext ctx, Response response, ApiResponse apiResponse) {
        if (apiResponse.getContent() == null)
            return null;
        AtomicBoolean matched;
        ValidationErrors errors = new ValidationErrors();
        apiResponse.getContent().forEach((s, mediaType) -> {
            try {
                errors.add(validateMediaType(ctx, s, mediaType, response));
            } catch (ParseException e) {
                errors.add(ctx.statusCode(500), format("Validating error. Something is wrong with the mediaType %s", mediaType));
            }
        });
        return errors;
    }

    private ValidationErrors validateMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Response response) throws ParseException {
        ValidationErrors errors = new ValidationErrors();

        if (response.getMediaType() == null) {
            errors.add(ctx.statusCode(500), "The response has a body, but no Content-Type header.");
            return errors;
        }

        // Check if the mediaType of the message is the same as the one declared for that status code
        // in the OpenAPI document.
        if (!response.isOfMediaType(mediaType)) {
            errors.add(ctx.statusCode(500).entityType(MEDIA_TYPE)
                            .entity(response.getMediaType().toString()),
                    format("Response with status code %d has mediatype %s instead of the expected type %s.", response.getStatusCode(), response.getMediaType(), mediaType));
            return errors;
        }
        errors.add(validateBodyAccordingToMediaType(ctx, mediaType, mediaTypeObj, response, 500));
        return errors;
    }
}