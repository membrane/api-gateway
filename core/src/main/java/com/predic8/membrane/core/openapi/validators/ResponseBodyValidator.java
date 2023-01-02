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

        if (operation.getResponses() == null)
            throw new RuntimeException("An operation should always have at least one response declared.");

        AtomicBoolean foundMatchingResponse = new AtomicBoolean();
        operation.getResponses().forEach((statusCode, responseSpec) -> {
            if (response.sameStatusCode(statusCode)) {
                foundMatchingResponse.set(true);
                if (responseSpec.getContent() != null) {
                    validateResponseBodyInternal(ctx, response, responseSpec);
                } else {
                    String ref = responseSpec.get$ref();
                    if (ref != null) {
                        validateResponseBodyInternal(ctx, response, api.getComponents().getResponses().get(getComponentLocalNameFromRef(ref)));
                    } else {
                        if (response.hasBody()) {
                            errors.add(ctx.statusCode(500),"Response should't have a body. There is no content described in the API specification.");
                        }
                    }
                }
            }
        });

        if (!foundMatchingResponse.get()) {
            errors.add(ctx.statusCode(500), format("Server returned a status code of %d but allowed are only %s",
                    response.getStatusCode(), joinByComma(operation.getResponses().keySet())));
        }

        return errors;
    }

    private void validateResponseBodyInternal(ValidationContext ctx, Response response, ApiResponse apiResponse) {

        if (apiResponse.getContent() == null)
            return;

        apiResponse.getContent().forEach((s, mediaType) -> {
            try {
                validateMediaType(ctx, s, mediaType, response);
            } catch (ParseException e) {
                errors.add(ctx.statusCode(500),format("Validating error. Something is wrong with the mediaType %s",mediaType));
            }
        });
    }

    private void validateMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Response response) throws ParseException {

        if (response.getMediaType() == null) {
            errors.add(ctx.statusCode(500),"The response has a body, but no Content-Type header.");
            return;
        }

        // Check if the mediaType of the message is the same as the one declared for that status code
        // in the OpenAPI document.
        if (!response.isOfMediaType(mediaType)) {
            errors.add(ctx.statusCode(500).validatedEntityType(MEDIA_TYPE)
                    .validatedEntity(response.getMediaType().toString()),
                    format("Response with status code %d has mediatype %s instead of the expected type %s.",response.getStatusCode(),response.getMediaType(),mediaType));
            return;
        }
        validateBodyAccordingToMediaType(ctx, mediaType, mediaTypeObj, response, 500);
    }
}
