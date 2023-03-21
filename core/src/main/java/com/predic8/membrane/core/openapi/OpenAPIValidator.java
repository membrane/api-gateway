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

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.UriUtil.normalizeUri;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.*;

public class OpenAPIValidator {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIValidator.class.getName());

    private final OpenAPI api;
    private final URIFactory uriFactory;
    private String basePath = "";

    public OpenAPIValidator(URIFactory uriFactory, OpenAPI api) {
        this.api = api;
        this.uriFactory = uriFactory;
        init();
    }

    public OpenAPIValidator(URIFactory uriFactory, InputStream openApiIs) {
        api = new OpenAPIParser().readContents(FileUtil.readInputStream(openApiIs), null, null).getOpenAPI();
        this.uriFactory = uriFactory;
        init();
    }

    public OpenAPIValidator(String openApiUrl, URIFactory uriFactory) {
        api = new OpenAPIParser().readLocation(openApiUrl, null, null).getOpenAPI();
        this.uriFactory = uriFactory;
        init();
    }

    /**
     * @TODO Handle basepath also for multiple server entries
     */
    private void init() {
        if (api.getServers() != null) {
            String url = api.getServers().get(0).getUrl();
            log.debug("Found server " + url);
            try {
                basePath = UriUtil.getPathFromURL(uriFactory,url);
            } catch (URISyntaxException e) {
                // @TODO
                throw new RuntimeException("Config Error ", e);
            }
        }
    }

    public ValidationErrors validate(Request request) {
        return validateMessage(request, null);
    }

    public ValidationErrors validateResponse(Request request, Response response) {
        return validateMessage(request, response);
    }

    private ValidationErrors validateMessage(Request req, Response response) {
        ValidationContext ctx = ValidationContext.fromRequest(req);

        ValidationErrors errors = new ValidationErrors();

        AtomicBoolean pathFound = new AtomicBoolean(false);
        api.getPaths().forEach((uriTemplate, pathItem) -> {

            // Path was already found so we do not need to check this uriTemplate
            if (pathFound.get()) {
                return;
            }

            try {
                req.parsePathParameters(normalizeUri(basePath + uriTemplate));
                pathFound.set(true);
            } catch (PathDoesNotMatchException ignore) {
                return;
            } catch (UnsupportedEncodingException e) {
                log.warn(format("Cannot decode path %s/%s. Request will be rejected.", basePath, uriTemplate));
                return;
            }

            errors.add(validateMethods(ctx.uriTemplate(uriTemplate), req, response, pathItem));

            // If there is no response we validate the request
            if (response == null) {
                PathParametersValidator pathParametersValidator = new PathParametersValidator(api);
                errors.add(pathParametersValidator.validatePathParameters(ctx.uriTemplate(uriTemplate), req, pathItem.getParameters()));
            }
        });

        if (!pathFound.get()) {
            errors.add(new ValidationError(ctx.validatedEntity(req.getPath()).validatedEntityType(PATH).statusCode(404), format("Path %s is invalid.", req.getPath())));
        }

        return errors;
    }

    private ValidationErrors validateMethods(ValidationContext ctx, Request req, Response response, PathItem pathItem) {
        ValidationErrors errors = new ValidationErrors();
        try {
            return errors.add(new OperationValidator(api).validateOperation(ctx, req, response, pathItem));
        } catch (MethodNotAllowException e) {
            return errors.add(ctx.statusCode(405)
                    .validatedEntity(req.getMethod())
                    .validatedEntityType(METHOD), format("Method %s is not allowed", req.getMethod()));
        }
    }
}