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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.servers.*;
import jakarta.mail.internet.*;
import org.slf4j.*;
import redis.clients.jedis.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.*;


public class OpenAPIInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIInterceptor.class.getName());

    protected final APIProxy apiProxy;

    public OpenAPIInterceptor(APIProxy apiProxy, Router router) {
        this.apiProxy = apiProxy;
        this.router = router;
    }


    public APIProxy getApiProxy() {
        return apiProxy;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String basePath = getMatchingBasePath(exc);
        // No matching API found
        if (basePath == null) {
            exc.setResponse(ProblemDetails.user(false)
                            .statusCode(404)
                            .addSubType("not-found")
                            .title("No matching API found!")
                            .detail("There is no API on the path %s deployed. Please check the path.".formatted(exc.getOriginalRequestUri()))
                            .extension("path",exc.getOriginalRequestUri())
                            .build());
            return RETURN;
        }

        OpenAPIRecord rec = apiProxy.getBasePaths().get(basePath);

        // If OpenAPIProxy has a <target> Element use this for routing otherwise
        // take the urls from the info.servers field in the OpenAPI document.
        if (!hasProxyATargetElement())
            setDestinationsFromOpenAPI(rec, exc);

        try {
            ValidationErrors errors = validateRequest(rec.api, exc);

            if (!errors.isEmpty()) {
                apiProxy.statisticCollector.collect(errors);
                return returnErrors(exc, errors, REQUEST, validationDetails(rec.api));
            }
        } catch (OpenAPIParsingException e) {
            exc.setResponse(ProblemDetails.internal(router.isProduction())
                    .detail("Could not parse OpenAPI with title %s. Check syntax and references.".formatted(rec.api.getInfo().getTitle()))
                    .exception(e)
                    .build());
            return RETURN;
        }
        catch (Throwable t /* No Purpose! Catch absolutely all */) {
            log.error("Message could not be validated against OpenAPI cause of an error during validation. Please check the OpenAPI with title %s.".formatted(rec.api.getInfo().getTitle()));
            log.error(t.getMessage(),t);
            exc.setResponse(ProblemDetails.internal(router.isProduction())
                    .detail("Message could not be validated against OpenAPI cause of an error during validation. Please check the OpenAPI with title %s.".formatted(rec.api.getInfo().getTitle()))
                    .exception(t)
                    .build());

            return RETURN;
        }

        exc.setProperty("openApi", rec);

        return CONTINUE;
    }

    private boolean hasProxyATargetElement() {
        return apiProxy.getTarget() != null && (apiProxy.getTarget().getHost() != null || apiProxy.getTarget().getUrl() != null);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {

        OpenAPIRecord rec = (OpenAPIRecord) exc.getProperty("openApi");

        try {
            ValidationErrors errors = validateResponse(rec.api, exc);

            if (errors != null && errors.hasErrors()) {
                exc.getResponse().setStatusCode(500); // A validation error in the response is a server error!
                apiProxy.statisticCollector.collect(errors);
                return returnErrors(exc, errors, RESPONSE, validationDetails(rec.api));
            }
        } catch (OpenAPIParsingException e) {
            exc.setResponse(ProblemDetails.internal(router.isProduction())
                    .detail("Could not parse OpenAPI with title %s. Check syntax and references.".formatted(rec.api.getInfo().getTitle()))
                    .exception(e)
                    .build());
            return RETURN;
        }
        catch (Throwable t /* On Purpose! Catch absolutely all */) {
            exc.setResponse(ProblemDetails.internal(router.isProduction())
                    .detail("Message could not be validated against OpenAPI cause of an error during validation. Please check the OpenAPI with title %s.".formatted(rec.api.getInfo().getTitle()))
                    .exception(t)
                    .build());
            return RETURN;
        }

        return CONTINUE;
    }

    protected String getMatchingBasePath(Exchange exc) {
        for (String basePath : apiProxy.getBasePaths().keySet()) {
            if (exc.getRequest().getUri().startsWith(basePath)) {
                return basePath;
            }
        }
        return null;
    }

    private ValidationErrors validateRequest(OpenAPI api, Exchange exc) throws IOException, ParseException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(api, REQUESTS))
            return errors;

        return new OpenAPIValidator(router.getUriFactory(), api).validate(getOpenapiValidatorRequest(exc));
    }

    private ValidationErrors validateResponse(OpenAPI api, Exchange exc) throws IOException, ParseException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(api, RESPONSES))
            return errors;
        return new OpenAPIValidator(router.getUriFactory(), api).validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
    }

    public boolean validationDetails(OpenAPI api) {
        if (api.getExtensions() == null)
            return true;

        @SuppressWarnings("unchecked")
        Map<String, Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        if (xValidation == null)
            return true;

        Boolean validationDetails = (Boolean) xValidation.get("details");

        if (xValidation.get("details") == null)
            return true;

        return validationDetails;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean shouldValidate(OpenAPI api, String direction) {
        Map<String, Object> extensions = api.getExtensions();
        if (extensions == null)
            return false;
        Map<String, Boolean> xValidation = getxValidation(extensions);
        return xValidation != null && xValidation.get(direction);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> getxValidation(Map<String, Object> extensions) {
        return (Map<String, Boolean>) extensions.get(X_MEMBRANE_VALIDATION);
    }

    protected void setDestinationsFromOpenAPI(OpenAPIRecord rec, Exchange exc) {
        exc.getDestinations().clear();
        rec.api.getServers().forEach(server -> {
            URL url = getServerUrlFromOpenAPI(rec, server);

            setHostHeader(exc, url); // @TODO Check

            exc.setProperty(SNI_SERVER_NAME, url.getHost());
            exc.getDestinations().add(getUrlWithoutPath(url) + exc.getRequest().getUri());
        });
    }

    private static URL getServerUrlFromOpenAPI(OpenAPIRecord rec, Server server) {
        try {
            if (rec.isVersion2()) {
                return new URL(parseSwaggersInfoServer(server.getUrl()).getUrl());
            }

            // OpenAPI 3 or newer
            return new URL(server.getUrl());
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse server address from OpenAPI " + server.getUrl());
        }
    }

    @Override
    public String getDisplayName() {
        return "OpenAPI";
    }

    @Override
    public String getShortDescription() {
        return "Autoconfiguration from OpenAPI";
    }

    @Override
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();

        sb.append("<table>");
        sb.append("<thead><th>API</th><th>Base Path</th><th>Validation</th></thead>");

        for (Map.Entry<String, OpenAPIRecord> entry : apiProxy.getBasePaths().entrySet()) {
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(entry.getValue().api.getInfo().getTitle());
            sb.append("</td>");
            sb.append("<td>");
            sb.append(entry.getKey());
            sb.append("</td>");
            sb.append("<td>");
            if (entry.getValue().api.getExtensions() != null && entry.getValue().api.getExtensions().get(X_MEMBRANE_VALIDATION) != null) {
                sb.append(entry.getValue().api.getExtensions().get(X_MEMBRANE_VALIDATION));
            }
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        return sb.toString();
    }

    private void setHostHeader(Exchange exc, URL url) {
        exc.getRequest().getHeader().setHost(new HostAndPort(url.getHost(), url.getPort()).toString());
    }

    private Outcome returnErrors(Exchange exc, ValidationErrors errors, ValidationErrors.Direction direction, boolean validationDetails) {
        exc.setResponse(Response.ResponseBuilder.newInstance().status(errors.get(0).getContext().getStatusCode(), "Bad Request").body(getErrorMessage(errors, direction, validationDetails)).contentType(APPLICATION_JSON_UTF8).build());
        return RETURN;
    }

    private byte[] getErrorMessage(ValidationErrors errors, ValidationErrors.Direction direction, boolean validationDetails) {
        if (validationDetails)
            return errors.getErrorMessage(direction);
        return createErrorMessage("Message validation failed!");
    }
}