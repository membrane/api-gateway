package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;

import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.getComponentLocalNameFromRef;
import static com.predic8.membrane.core.openapi.util.Utils.joinByComma;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.MEDIA_TYPE;
import static java.lang.String.format;

public class ResponseBodyValidator {

    OpenAPI api;
    ValidationErrors errors = new ValidationErrors();

    public ResponseBodyValidator(OpenAPI api) {
        this.api = api;
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

            Set<String> statusCodesInSpec = operation.getResponses().keySet();

            errors.add(ctx.statusCode(500),String.format("Server returned a status code of %d but allowed are only %s",response.getStatusCode(), joinByComma(statusCodesInSpec)));
        }

        return errors;
    }

    private void validateMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Response response) {

        if (response.getMediaType() == null) {
            errors.add(ctx.statusCode(500),"The response has a body, but no Content-Type header.");
            return;
        }

        if (!response.getMediaType().equalsIgnoreCase(mediaType)) {
            errors.add(ctx.statusCode(500).validatedEntityType(MEDIA_TYPE).validatedEntity(response.getMediaType()), format("Response with status code %d has mediatype %s instead of the expected type %s.",response.getStatusCode(),response.getMediaType(),mediaType));
            return;
        }

        if (mediaType.equals("application/json")) {
            if (mediaTypeObj.getSchema().get$ref() != null) {
                ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx.statusCode(500), response.getBody()));
        }
    }

    private void validateResponseBodyInternal(ValidationContext ctx, Response response, ApiResponse apiResponse) {

        if (apiResponse.getContent() == null)
            return;

        apiResponse.getContent().forEach((s, mediaType) -> validateMediaType(ctx, s, mediaType, response));
    }
}
