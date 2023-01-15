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

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.model.Message;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;

import static com.predic8.membrane.core.http.MimeType.*;

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
        } else if(isXML(mediaType)) {
            errors.add(ctx.statusCode(statusCode),"Validation of XML messages is not implemented yet!");
        } else if(isWWWFormUrlEncoded(mediaType)) {
            errors.add(ctx.statusCode(statusCode),"Validation of 'application/x-www-form-urlencoded' messages is not implemented yet!");
        }
        // Other types that can't be validated against OpenAPI are Ok.
    }
}
