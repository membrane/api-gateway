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

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static com.predic8.membrane.core.util.MediaTypeUtil.*;
import static java.lang.String.*;

public abstract class AbstractBodyValidator<T extends Message<? extends Body,?>> {

    protected final OpenAPI api;

    public abstract int getDefaultStatusCode();

    public abstract String getMessageName();

    public AbstractBodyValidator(OpenAPI api) {
        this.api = api;
    }

    protected ValidationErrors validateBodyAccordingToMediaType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Message<?,?> message) {
        ValidationErrors errors = new ValidationErrors();

        // Use the value of the OpenAPI spec for comparison, so it can not
        // be influenced from the outside.
        if ( APPLICATION_JSON_CONTENT_TYPE.match(mediaType)) {
            if (mediaTypeObj.getSchema() != null && mediaTypeObj.getSchema().get$ref() != null) {
                ctx = ctx.schemaType(mediaTypeObj.getSchema().get$ref());
            }
            errors.add(new SchemaValidator(api, mediaTypeObj.getSchema()).validate(ctx, message.getBody()));
        } else if(isXML(mediaType)) {
            errors.add(ctx,"Validation of XML messages is not implemented yet!");
        } else if(isWWWFormUrlEncoded(mediaType)) {
            errors.add(ctx,"Validation of 'application/x-www-form-urlencoded' messages is not implemented yet!");
        }
        // Other types that can't be validated against OpenAPI are Ok.
        return errors;
    }

    protected ValidationErrors validateContentTypeHeader(ValidationContext ctx, Message<?,?> message)  {
        ValidationErrors errors = new ValidationErrors();
        if (message.getMediaType() == null) {
            errors.add(ctx.statusCode(getDefaultStatusCode()), getMessageName() + " has a body, but no Content-Type header.");
            return errors;
        }
        if (message.getMediaType().getBaseType().equals("*") || message.getMediaType().getSubType().equals("*")) {
            errors.add(ctx.statusCode(getDefaultStatusCode()), "Content-Type %s is not concrete.".formatted(message.getMediaType()));
        }
        return errors;
    }

    protected ValidationErrors validateMediaTypeForMessageType(ValidationContext ctx, String mediaType, MediaType mediaTypeObj, Message<?,?> response) {
        ValidationErrors errors = new ValidationErrors();
        // Check if the mediaType of the message is the same as the one declared for that status code
        // in the OpenAPI document.
        if (!response.isOfMediaType(mediaType)) {
            errors.add(ctx.entityType(MEDIA_TYPE)
                            .entity(response.getMediaType().toString()),
                    format("Message has media type %s instead of the expected type %s.", response.getMediaType(), mediaType));
            return errors;
        }
        errors.add(validateBodyAccordingToMediaType(ctx.statusCode(getDefaultStatusCode()), mediaType, mediaTypeObj, response));
        return errors;
    }

    protected ValidationErrors validateBodyInternal(ValidationContext ctx, Message<?,?> msg, Content content) {
        if (content == null)
            return null;

        ValidationErrors errors = validateContentTypeHeader(ctx.entityType(BODY), msg);
        if (!errors.isEmpty())
            return errors;

        String mostSpecificMediaType;
        try {
            mostSpecificMediaType = getMostSpecificMediaType(msg.getMediaType().toString(), content.keySet()).orElseThrow();
        } catch (Exception e) {
            return ValidationErrors.create(ctx.statusCode(getStatusCodeForWrongMediaType()).entityType(MEDIA_TYPE).entity(msg.getMediaType().toString()),  "The media type(Content-Type header) of the %s does not match any of %s.".formatted(getMessageName(), content.keySet()));
        }

        return validateMediaTypeForMessageType(ctx.statusCode(getStatusCodeForWrongMediaType()), mostSpecificMediaType, content.get(mostSpecificMediaType), msg);
    }

    /**
     * If the media type is not supported the status code should be different for requests and responses
     */
    protected abstract int getStatusCodeForWrongMediaType();

}
