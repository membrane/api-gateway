package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;

public class ResponseBodyValidator {

    OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public ResponseBodyValidator(OpenAPI api) {
        this.api = api;
    }

    ValidationErrors validateResponseBody(ValidationContext ctx, Response response, Operation operation) {

        if (operation.getResponses() == null)
            throw new RuntimeException("An operation should always have at least one response declared.");

        operation.getResponses().entrySet().forEach(responseEntry -> {
            if (response.sameStatusCode(responseEntry.getKey())) {
                if (responseEntry.getValue().getContent() != null) {
                    validateResponseBodyInternal(ctx, response, responseEntry.getValue());
                } else {
                    String ref = responseEntry.getValue().get$ref();
                    if (ref != null) {
                        validateResponseBodyInternal(ctx, response, api.getComponents().getResponses().get(getComponentLocalNameFromRef(ref)));
                    }
                }
            }
        });
        return errors;
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

    private void validateResponseBodyInternal(ValidationContext ctx, Message message, ApiResponse apiResponse) {

        if (apiResponse.getContent() == null)
            return;

        ValidationErrors errors = new ValidationErrors();
        apiResponse.getContent().forEach((s, mediaType) -> {
            validateMediaType(ctx, s, mediaType, message.getBody());
        });
    }
}
