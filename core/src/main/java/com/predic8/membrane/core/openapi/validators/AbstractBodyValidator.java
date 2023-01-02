package com.predic8.membrane.core.openapi.validators;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.model.Message;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_CONTENT_TYPE;

public class AbstractBodyValidator<T> {

    protected OpenAPI api;
    protected ValidationErrors errors = new ValidationErrors();

    public AbstractBodyValidator(OpenAPI api) {
        this.api = api;
    }

    protected void validateBodyAccordingToMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Message<T> message, int statusCode) {
        // Use the value of the OpenAPI spec for comparison, so it can not
        // be influenced from the outside.
        if ( APPLICATION_JSON_CONTENT_TYPE.match(mediaType)) {
            if (mediaTypeObj.getSchema().get$ref() != null) {
                ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx.statusCode(statusCode), message.getBody()));
        } else if(MimeType.isXML(mediaType)) {
            errors.add(ctx.statusCode(statusCode),"Validation of XML messages is not implemented yet!");
        } else if(MimeType.isWWWFormUrlEncoded(mediaType)) {
            errors.add(ctx.statusCode(statusCode),"Validation of 'application/x-www-form-urlencoded' messages is not implemented yet!");
        }
        // Other types that can't be validated against OpenAPI are Ok.
    }
}
