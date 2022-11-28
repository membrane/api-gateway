package com.predic8.membrane.core.openapi;

import com.predic8.membrane.core.openapi.model.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.openapi.validators.*;
import com.predic8.membrane.core.util.*;
import io.swagger.parser.*;
import io.swagger.v3.oas.models.*;
import org.slf4j.*;

import java.io.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationContext.ValidatedEntityType.*;
import static java.lang.String.*;

public class OpenAPIValidator {

    private static Logger log = LoggerFactory.getLogger(OpenAPIValidator.class.getName());

    private OpenAPI api;
    private UriTemplateMatcher uriTemplateMatcher = new UriTemplateMatcher();
    private String basePath = "";

    public OpenAPIValidator(OpenAPI api) {
        this.api = api;
        init();
    }

    public OpenAPIValidator(InputStream openApiIs) {
        api = new OpenAPIParser().readContents(FileUtil.readInputStream(openApiIs), null, null).getOpenAPI();
        init();
    }

    public OpenAPIValidator(String openApiUrl) {
        api = new OpenAPIParser().readLocation(openApiUrl, null, null).getOpenAPI();
        init();
    }

    /**
     * @TODO Handle basepath also for multiple server entries
     */
    private void init() {
        if (api.getServers() != null) {
            String url = api.getServers().get(0).getUrl();
            log.debug("Found server " + url);
            basePath = getPathFromURL(url);
        }
    }

    public ValidationErrors validate(Request request) {
        return validateMessage(request, null);
    }

    public ValidationErrors validateResponse(Request request, Response response) {
        return validateMessage(request, response);
    }

    private ValidationErrors validateMessage(Request req, Response response) {

        req.ajustPathAccordingToBasePath(basePath);

        ValidationContext ctx = ValidationContext.fromRequest(req);

        ValidationErrors errors = new ValidationErrors();

        AtomicBoolean pathFound = new AtomicBoolean(false);
        api.getPaths().forEach((uriTemplate, pathItem) -> {

            // Path was already found so we do not need to check this uriTemplate
            if (pathFound.get()) {
                return;
            }

            try {
                req.parsePathParameters(uriTemplate);
            } catch (PathDoesNotMatchException e) {
                return;
            }

            pathFound.set(true);

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
            OperationValidator operationValidator = new OperationValidator(api);
            return errors.add(operationValidator.validateOperation(ctx, req, response, pathItem));
        } catch (MethodNotAllowException e) {
            return errors.add(ctx.statusCode(405)
                    .validatedEntity(req.getMethod())
                    .validatedEntityType(METHOD), format("Method %s is not allowed", req.getMethod()));
        }
    }
}