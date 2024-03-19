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
import com.predic8.membrane.core.openapi.util.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.*;

public class RequestBodyValidator extends AbstractBodyValidator<Request<? extends Body>> {

    @Override
    public int getDefaultStatusCode() {
        return 400;
    }

    @Override
    public String getNessageName() {
        return "Request";
    }

    public RequestBodyValidator(OpenAPI api) {
        super(api);
    }

    ValidationErrors validateRequestBody(ValidationContext ctx, Operation operation, Request<?> request) {
        ValidationErrors errors = new ValidationErrors();
        if (operation.getRequestBody() == null) {
            if (!request.hasBody())
                return errors;
            else
                return errors.add(ctx.statusCode(400),"Request has a body although it should't.");
        }

        if (operation.getRequestBody().getContent() != null) {
            errors.add(validateRequestBodyInternal(ctx, request, operation.getRequestBody()));
        } else {
            String ref = operation.getRequestBody().get$ref();
            if (ref != null) {
                errors.add(validateRequestBodyInternal(ctx, request, getRequestBodyFromSchema(ref)));
            } else {
                throw new RuntimeException("Should not happen!");
            }
        }

        return errors;
    }

    private RequestBody getRequestBodyFromSchema(String ref) {
        return api.getComponents().getRequestBodies().get(Utils.getComponentLocalNameFromRef(ref));
    }

    private ValidationErrors validateRequestBodyInternal(ValidationContext ctx, Request<?> request, RequestBody requestBody) {
        ValidationErrors errors = new ValidationErrors();
        requestBody.getContent().forEach((s, mediaType) -> errors.add(validateMediaType(ctx, s, mediaType, request)));
        return errors;
    }

    protected ValidationErrors validateMediaTypeForMessageType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Request<?> request) {
        ValidationErrors errors = new ValidationErrors();
        if (!request.isOfMediaType(mediaType)) {
            errors.add(ctx.statusCode(415).entityType(MEDIA_TYPE).entity(request.getMediaType().toString()), format("Request has mediatype %s instead of the expected type %s.",request.getMediaType(),mediaType));
            return errors;
        }
        errors.add(validateBodyAccordingToMediaType(ctx, mediaType, mediaTypeObj, request, 400));
        return errors;
    }
}