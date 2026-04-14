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

package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.Body;
import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.util.MethodNotAllowException;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.openapi.util.UriUtil;
import com.predic8.membrane.core.openapi.validators.OperationValidator;
import com.predic8.membrane.core.openapi.validators.ValidationContext;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Map;

import static com.predic8.membrane.core.openapi.util.UriUtil.normalizeUri;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.fromRequest;
import static java.lang.String.format;

public class OpenAPIValidator {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIValidator.class.getName());

    private final OpenAPIRecord rec;
    private final URIFactory uriFactory;

    private String basePath = "";

    public OpenAPIValidator(URIFactory uriFactory, OpenAPIRecord rec) {
        this.rec = rec;
        this.uriFactory = uriFactory;
        init();
    }

    /**
     * @TODO Handle basepath also for multiple server entries
     */
    private void init() {
        if (rec.getApi().getServers() != null) {
            log.debug("Found server {}", getUrl());
            try {
                basePath = UriUtil.getPathFromURL(uriFactory, getUrl());
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Error initializing OpenAPI validator. Path was: %s ".formatted(getUrl()), e);
            }
        }
    }

    private String getUrl() {
        if (rec.getSpec().hasRewrite() && rec.getSpec().getRewrite().getBasePath() != null)
            return rec.getSpec().getRewrite().getBasePath();

        return rec.getApi().getServers().getFirst().getUrl();
    }

    public ValidationErrors validate(Request<? extends Body> request) {
        return prepareValidation(request).validateRequest(request);
    }

    public ValidationErrors validateResponse(Request<? extends Body> request, Response<? extends Body> response) {
        return prepareValidation(request).validateResponse(response);
    }

    /**
     * Prepares a plan to later use it for request or response validation separately.
     * Must be called for both request and response validation. If there is response but no
     * request validation the operation must be found first in order to validate against the
     * proper operation.
     */
    public ValidationPlan prepareValidation(Request<? extends Body> request) {
        for (Map.Entry<String, PathItem> path : rec.getApi().getPaths().entrySet()) {
            try {
                return prepareValidationForMatchingPath(request, path.getKey(), path.getValue());
            } catch (PathDoesNotMatchException ignored) {
                // All paths from the OpenAPI that do not match will cause an exception while parsing the path and parameter. Then the next uriTemplate
                // is tried until we get a match or there is no matching path in the OpenAPI.
            }
        }

        return ValidationPlan.error(ValidationErrors.error(fromRequest(request)
                .entity(request.getPath())
                .entityType(PATH)
                .statusCode(404), format("Path %s is invalid.", request.getPath())));
    }

    private ValidationPlan prepareValidationForMatchingPath(Request<? extends Body> request, String uriTemplate, PathItem pathItem) throws PathDoesNotMatchException {
        String pathTemplate = normalizeUri(basePath + uriTemplate);
        request.parsePathParameters(pathTemplate);

        var ctx = fromRequest(request).uriTemplate(uriTemplate);
        try {
            return ValidationPlan.create(pathTemplate, ctx, OperationValidator.create(rec.getApi(), request.getMethod(), pathItem));
        } catch (MethodNotAllowException e) {
            return ValidationPlan.error(ValidationErrors.error(ctx.statusCode(405)
                    .entity(request.getMethod())
                    .entityType(METHOD), format("Method %s is not allowed", request.getMethod())));
        }
    }

    public static final class ValidationPlan {

        private final String pathTemplate;
        private final ValidationContext ctx;
        private final OperationValidator validator;

        /**
         * TODO Explain
         */
        private final ValidationErrors errors;

        private ValidationPlan(String pathTemplate, ValidationContext ctx, OperationValidator validator, ValidationErrors errors) {
            this.pathTemplate = pathTemplate;
            this.ctx = ctx;
            this.validator = validator;
            this.errors = errors;
        }

        private static ValidationPlan create(String pathTemplate, ValidationContext ctx, OperationValidator operationValidator) {
            return new ValidationPlan(pathTemplate, ctx, operationValidator, ValidationErrors.empty());
        }

        private static ValidationPlan error(ValidationErrors errors) {
            return new ValidationPlan(null, null, null, errors);
        }

        public ValidationErrors validateRequest(Request<? extends Body> request) {
            // There might be already path and method validation errors.
            if (errors.hasErrors())
                return errors;

            try {
                request.parsePathParameters(pathTemplate);
            } catch (PathDoesNotMatchException e) {
                throw new IllegalStateException("ValidationPlan no longer matches request path %s.".formatted(request.getPath()), e);
            }

            return validator.validateRequest(ctx, request);
        }

        public ValidationErrors validateResponse(Response<? extends Body> response) {
            // There might be already path and method validation errors.
            if (errors.hasErrors())
                return errors;
            return validator.validateResponse(ctx, response);
        }

        public ValidationErrors getErrors() {
            return errors;
        }
    }

    public OpenAPI getApi() {
        return rec.getApi();
    }
}
