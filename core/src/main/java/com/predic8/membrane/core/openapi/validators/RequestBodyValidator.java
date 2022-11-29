package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;

import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.MEDIA_TYPE;

public class RequestBodyValidator {

    private OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public RequestBodyValidator(OpenAPI api) {
        this.api = api;
    }

    ValidationErrors validateRequestBody(ValidationContext ctx, Operation operation, Request request) {

        if (operation.getRequestBody() == null) {
            if (!request.hasBody())
                return errors;
            else
                return errors.add(ctx,"Request has a body although it should't.");
        }

        if (operation.getRequestBody().getContent() != null) {
            validateRequestBodyInternal(ctx, request, operation.getRequestBody());
        } else {
            String ref = operation.getRequestBody().get$ref();
            if (ref != null) {
                validateRequestBodyInternal(ctx, request, getRequestBodyFromSchema(ref));
            } else {
                throw new RuntimeException("Should not happen!");
            }
        }

        return errors;
    }

    private RequestBody getRequestBodyFromSchema(String ref) {
        return api.getComponents().getRequestBodies().get(Utils.getComponentLocalNameFromRef(ref));
    }

    private void validateRequestBodyInternal(ValidationContext ctx, Request request, RequestBody requestBody) {
        requestBody.getContent().forEach((s, mediaType) -> validateMediaType(ctx, s, mediaType, request));
    }

    private void validateMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Request request) {

        if (!request.getMediaType().equalsIgnoreCase(mediaType)) {
            errors.add(ctx.statusCode(415).validatedEntityType(MEDIA_TYPE).validatedEntity(request.getMediaType()), String.format("Request has mediatype %s instead of the expected type %s.",request.getMediaType(),mediaType));
            return;
        }

        if (mediaType.equals("application/json")) {
            if (mediaTypeObj.getSchema().get$ref() != null) {
                ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx.statusCode(400), request.getBody()));
        }
    }
}
