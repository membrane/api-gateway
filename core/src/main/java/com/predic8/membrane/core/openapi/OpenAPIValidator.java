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
import com.predic8.membrane.core.openapi.util.UriTemplate;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.PARAM_NAME_REGEX;
import static com.predic8.membrane.core.openapi.util.UriUtil.normalizeUri;
import static com.predic8.membrane.core.openapi.validators.OperationValidator.getMethods;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.METHOD;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.PATH;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.fromRequest;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

public class OpenAPIValidator {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIValidator.class.getName());

    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile(PARAM_NAME_REGEX);

    private final OpenAPIRecord rec;
    private final URIFactory uriFactory;

    private String basePath = "";

    /**
     * Paths of the OpenAPI, concrete before templated paths, otherwise in document order, compiled once.
     * A matching concrete path wins. Among matching templated paths, the first that has the method of the request wins.
     */
    private final List<PathTemplate> pathTemplates;

    /**
     * @param uriTemplate path as written in the OpenAPI, without base path
     * @param template    base path and uriTemplate, compiled
     */
    private record PathTemplate(String uriTemplate, UriTemplate template, PathItem pathItem) {}

    /**
     * Thread safe, create once per OpenAPI and reuse it for all requests.
     */
    public OpenAPIValidator(URIFactory uriFactory, OpenAPIRecord rec) {
        this.rec = rec;
        this.uriFactory = uriFactory;
        init();
        pathTemplates = compilePathTemplates();
        checkForEquivalentPaths();
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

    /**
     * OpenAPI: concrete paths are matched before their templated counterparts. Otherwise the order of the OpenAPI is kept.
     */
    private List<PathTemplate> compilePathTemplates() {
        return rec.getApi().getPaths().entrySet().stream()
                .sorted(comparing(e -> isTemplated(e.getKey())))
                .map(e -> new PathTemplate(e.getKey(), compile(normalizeUri(basePath + e.getKey())), e.getValue()))
                .toList();
    }

    private static boolean isTemplated(String path) {
        return path.contains("{");
    }

    /**
     * OpenAPI: templated paths with the same hierarchy but different templated names MUST NOT exist as they are identical.
     */
    private void checkForEquivalentPaths() {
        final var pathsWithoutNames = new HashMap<String, String>();
        for (final var path : pathTemplates) {
            final var equivalent = pathsWithoutNames.putIfAbsent(withoutParameterNames(path.uriTemplate()), path.uriTemplate());
            if (equivalent != null)
                throw new ConfigurationException("Paths %s and %s of OpenAPI %s are identical, they differ only in parameter names.".formatted(equivalent, path.uriTemplate(), describeOpenAPI()));
        }
    }

    private static String withoutParameterNames(String path) {
        return PARAM_NAME_PATTERN.matcher(path).replaceAll("{}");
    }

    private UriTemplate compile(String pathTemplate) {
        try {
            return UriTemplate.of(pathTemplate);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Cannot use path %s of OpenAPI %s as URI template: %s".formatted(pathTemplate, describeOpenAPI(), e.getMessage()), e);
        }
    }

    private String describeOpenAPI() {
        final var title = rec.getApi().getInfo() != null ? rec.getApi().getInfo().getTitle() : null;
        final var location = rec.getSpec() != null ? rec.getSpec().getLocation() : null;
        return location == null ? "'%s'".formatted(title) : "'%s' (%s)".formatted(title, location);
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
        final var selection = select(request.getMethod(), request.getPath());
        if (selection == null)
            return ValidationPlan.error(ValidationErrors.error(fromRequest(request)
                    .entity(request.getPath())
                    .entityType(PATH)
                    .statusCode(404), format("Path %s is invalid.", request.getPath())));

        final var path = selection.path();
        request.parsePathParameters(path.template());
        if (selection.operation() == null)
            return methodNotAllowed(request, path, getAllowedMethods(request.getPath(), path));

        return ValidationPlan.create(path.template(), contextFor(request, path), selection.operation());
    }

    /**
     * @param path path of the request, may contain a query string, which is ignored
     * @return the path as written in the OpenAPI that a request is validated against, or null if no path matches. The
     * path may lack an operation for the method.
     */
    public String getMatchingPath(String method, String path) {
        final var selection = select(method, path);
        return selection == null ? null : selection.path().uriTemplate();
    }

    /**
     * @param operation null if the path has no operation for the method
     */
    private record Selection(PathTemplate path, OperationValidator operation) {}

    private Selection select(String method, String path) {
        PathTemplate firstMatchingPath = null;
        for (final var pathTemplate : pathTemplates) {
            if (!pathTemplate.template().matches(path))
                continue;
            try {
                return new Selection(pathTemplate, OperationValidator.create(rec.getApi(), method, pathTemplate.pathItem()));
            } catch (MethodNotAllowException e) {
                // A matching concrete path takes precedence over templated ones, even without the method
                if (!isTemplated(pathTemplate.uriTemplate()))
                    return new Selection(pathTemplate, null);
                // Ambiguous templated paths are left to the tooling by OpenAPI: try another matching path that has the method
                if (firstMatchingPath == null)
                    firstMatchingPath = pathTemplate;
            }
        }
        return firstMatchingPath == null ? null : new Selection(firstMatchingPath, null);
    }

    /**
     * A templated path is selected without the method only if no matching path has it, so all of them are offered.
     */
    private Collection<String> getAllowedMethods(String path, PathTemplate selected) {
        if (!isTemplated(selected.uriTemplate()))
            return getMethods(selected.pathItem());

        final var allowedMethods = new LinkedHashSet<String>();
        for (final var pathTemplate : pathTemplates) {
            if (pathTemplate.template().matches(path))
                allowedMethods.addAll(getMethods(pathTemplate.pathItem()));
        }
        return allowedMethods;
    }

    private static ValidationPlan methodNotAllowed(Request<? extends Body> request, PathTemplate path, Collection<String> allowedMethods) {
        return ValidationPlan.error(ValidationErrors.error(contextFor(request, path).statusCode(405)
                .allowedMethods(allowedMethods)
                .entity(request.getMethod())
                .entityType(METHOD), format("Method %s is not allowed", request.getMethod())));
    }

    private static ValidationContext contextFor(Request<? extends Body> request, PathTemplate path) {
        return fromRequest(request).uriTemplate(path.uriTemplate());
    }

    public static final class ValidationPlan {

        private final UriTemplate pathTemplate;
        private final ValidationContext ctx;
        private final OperationValidator validator;

        /**
         * TODO Explain
         */
        private final ValidationErrors errors;

        private ValidationPlan(UriTemplate pathTemplate, ValidationContext ctx, OperationValidator validator, ValidationErrors errors) {
            this.pathTemplate = pathTemplate;
            this.ctx = ctx;
            this.validator = validator;
            this.errors = errors;
        }

        private static ValidationPlan create(UriTemplate pathTemplate, ValidationContext ctx, OperationValidator operationValidator) {
            return new ValidationPlan(pathTemplate, ctx, operationValidator, ValidationErrors.empty());
        }

        private static ValidationPlan error(ValidationErrors errors) {
            return new ValidationPlan(null, null, null, errors);
        }

        public ValidationErrors validateRequest(Request<? extends Body> request) {
            // There might be already path and method validation errors.
            if (errors.hasErrors())
                return errors;

            if (!request.parsePathParameters(pathTemplate))
                throw new IllegalStateException("ValidationPlan no longer matches request path %s.".formatted(request.getPath()));

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
