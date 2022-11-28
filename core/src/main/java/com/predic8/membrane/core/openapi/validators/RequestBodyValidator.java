package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;

public class RequestBodyValidator {

    private OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public RequestBodyValidator(OpenAPI api) {
        this.api = api;
    }

    ValidationErrors validateRequestBody(ValidationContext ctx, Operation operation, Request request) {

        if (operation.getRequestBody() == null)
            return errors;

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
        requestBody.getContent().forEach((s, mediaType) -> {
            validateMediaType(ctx, s, mediaType, request.getBody());
        });
    }

    private void validateMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Body body) {
        // TODO Prüfung MediaType gegen header einbauen.
        if (mediaType.equals("application/json")) {
            if (mediaTypeObj.getSchema().get$ref() != null) {
                ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx.statusCode(400), body));
        }
    }
}
