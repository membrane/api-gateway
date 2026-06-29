/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
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

import com.predic8.membrane.core.openapi.model.Request;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.openapi.validators.QueryParameterValidator.getQueryString;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.QUERY_PARAMETER;

/**
 * Validates the OpenAPI 3.2 {@code in: querystring} parameter, which treats the entire URL query
 * string as a single value described by a {@code content} media type (most often
 * {@code application/x-www-form-urlencoded}). The raw query string is validated with the same logic
 * as a urlencoded body via {@link FormUrlEncodedValidator}.
 */
public class QuerystringParameterValidator {

    private final OpenAPI api;

    public QuerystringParameterValidator(OpenAPI api) {
        this.api = api;
    }

    ValidationErrors validate(ValidationContext ctx, Request<?> request, Parameter parameter) {
        if (parameter.getContent() == null || parameter.getContent().isEmpty()) {
            return ValidationErrors.empty();
        }
        ctx = ctx.entityType(QUERY_PARAMETER).statusCode(400)
                .entity(parameter.getName() != null ? parameter.getName() : "querystring");

        MediaType mediaType = parameter.getContent().get(APPLICATION_X_WWW_FORM_URLENCODED);
        if (mediaType == null) {
            // The query string is always urlencoded on the wire; use the only declared media type.
            mediaType = parameter.getContent().values().iterator().next();
        }

        String query = getQueryString(request);
        return new FormUrlEncodedValidator(api).validate(ctx, mediaType, query != null ? query : "");
    }
}
