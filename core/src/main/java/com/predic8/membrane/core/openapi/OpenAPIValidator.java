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

import com.predic8.membrane.core.openapi.model.Request;
import com.predic8.membrane.core.openapi.model.Response;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.MethodNotAllowException;
import com.predic8.membrane.core.openapi.util.PathDoesNotMatchException;
import com.predic8.membrane.core.openapi.util.UriUtil;
import com.predic8.membrane.core.openapi.validators.OperationValidator;
import com.predic8.membrane.core.openapi.validators.PathParametersValidator;
import com.predic8.membrane.core.openapi.validators.ValidationContext;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
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
            log.debug("Found server " + getUrl());
            try {
                basePath = UriUtil.getPathFromURL(uriFactory, getUrl());
            } catch (URISyntaxException e) {
                // @TODO
                throw new RuntimeException("Config Error ", e);
            }
        }
    }

    private String getUrl() {
        if (rec.getSpec().hasRewrite() && rec.getSpec().getRewrite().getBasePath() != null)
            return rec.getSpec().getRewrite().getBasePath();

        return rec.getApi().getServers().get(0).getUrl();
    }

    public ValidationErrors validate(Request request) {
        return validateMessage(request, null);
    }

    public ValidationErrors validateResponse(Request request, Response response) {
        return validateMessage(request, response);
    }

    private ValidationErrors validateMessage(Request req, Response res) {

        for (Map.Entry<String, PathItem> path : rec.getApi().getPaths().entrySet()) {
            try {
                return validateMethodsAndParametersIfPathMatches(req, res, path.getKey(), path.getValue());
            } catch (PathDoesNotMatchException ignored) {
                // All paths from the OpenAPI that do not match will cause an exception while parsing the path and parameter. Then the next uriTemplate
                // is tried until we get a match or there is no matching path in the OpenAPI.
            }
        }

        return ValidationErrors.create( ValidationContext.fromRequest(req)
                .entity(req.getPath())
                .entityType(PATH)
                .statusCode(404), format("Path %s is invalid.", req.getPath()));
    }

    private ValidationErrors validateMethodsAndParametersIfPathMatches(Request req, Response response, String uriTemplate, PathItem pathItem) throws PathDoesNotMatchException {

        // Throws exception if path or parameters do not match
        req.parsePathParameters(normalizeUri(basePath + uriTemplate));

        ValidationContext ctx = ValidationContext.fromRequest(req);

        ValidationErrors errors = validateMethods(ctx.uriTemplate(uriTemplate), req, response, pathItem);

        // If there is no response it is a request by logic, so we validate the request parameters
        if (response == null) {
            errors.add(new PathParametersValidator(rec.getApi()).validatePathParameters(ctx.uriTemplate(uriTemplate), req, pathItem.getParameters()));
        }
        return errors;
    }

    private ValidationErrors validateMethods(ValidationContext ctx, Request req, Response response, PathItem pathItem) {
        ValidationErrors errors = new ValidationErrors();
        try {
            return errors.add(new OperationValidator(rec.getApi()).validateOperation(ctx, req, response, pathItem));
        } catch (MethodNotAllowException e) {
            return errors.add(ctx.statusCode(405)
                    .entity(req.getMethod())
                    .entityType(METHOD), format("Method %s is not allowed", req.getMethod()));
        }
    }

    public OpenAPI getApi() {
        return rec.getApi();
    }
}