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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.OpenAPIParsingException;
import com.predic8.membrane.core.openapi.OpenAPIValidator;
import com.predic8.membrane.core.openapi.validators.ValidationErrors;
import com.predic8.membrane.core.rules.RuleKey;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.predic8.membrane.core.exchange.Exchange.SNI_SERVER_NAME;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.OpenAPIUtil.parseSwaggersInfoServer;
import static com.predic8.membrane.core.openapi.util.UriUtil.getUrlWithoutPath;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.REQUEST;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.RESPONSE;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.*;


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
            ValidationErrors errors = validateRequest(rec, exc);

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
        catch (Throwable t /* On purpose! Catch absolutely all */) {
            final String LOG_MESSAGE = "Message could not be validated against OpenAPI cause of an error during validation. Please check the OpenAPI with title %s.";
            log.error(LOG_MESSAGE.formatted(rec.api.getInfo().getTitle()));
            log.error(t.getMessage(),t);
            exc.setResponse(ProblemDetails.internal(router.isProduction())
                    .detail(LOG_MESSAGE.formatted(rec.api.getInfo().getTitle()))
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
            ValidationErrors errors = validateResponse(rec, exc);

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

    private ValidationErrors validateRequest(OpenAPIRecord rec, Exchange exc) throws IOException, ParseException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(rec.getApi(), REQUESTS))
            return errors;

        return new OpenAPIValidator(router.getUriFactory(), rec ).validate(getOpenapiValidatorRequest(exc));
    }

    private ValidationErrors validateResponse(OpenAPIRecord rec, Exchange exc) throws IOException, ParseException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(rec.getApi(), RESPONSES))
            return errors;
        return new OpenAPIValidator(router.getUriFactory(), rec).validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
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
        sb.append("<thead><th>API</th><th>Base Paths</th><th>Properties</th></thead>");
        for (Map.Entry<OpenAPIRecord, List<String>> entry : apiProxy.getBasePaths().entrySet().stream()
                .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toList()))).entrySet()) {
            OpenAPIRecord value = entry.getKey();
            List<String> keys = entry.getValue();
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(value.api.getInfo().getTitle());
            sb.append("</td>");
            sb.append("<td>");
            keys.stream().sorted().forEach(keyList -> sb.append(keyList).append("<br />"));
            sb.append("</td>");
            sb.append("<td>");
            sb.append("<b>SwaggerUI: </b>");
            sb.append("<a href='").append(buildSwaggerUrl(value.api)).append("'>").append(buildSwaggerUrl(value.api)).append("</a>");
            sb.append("<br /> <br />");
            sb.append("<b> Validation Configuration: </b>");
            sb.append("<br />");
            if (value.api.getExtensions() != null && value.api.getExtensions().get(X_MEMBRANE_VALIDATION) != null) {
                //noinspection unchecked
                sb.append(buildValidationPropertiesDescription((Map<String, Object>) value.api.getExtensions().get(X_MEMBRANE_VALIDATION)));
            }
            sb.append("<br />");
            sb.append("<b>Server: </b>");
            value.getApi().getServers().stream()
                    .sorted(comparing(Server::getUrl))
                    .forEach(s -> sb.append("<br /> - <a href='").append(s.getUrl()).append("'>").append(s.getUrl()).append("</a>"));
            sb.append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");

        return sb.toString();
    }

    public String buildSwaggerUrl(OpenAPI api) {
        String protocol = "";
        String host = !Objects.equals(getKey().getHost(), "*") ? getKey().getHost() : "localhost";
        if(!(host.contains("http://") || host.contains("https://"))) {
            protocol = router.getParentProxy(this).getSslInboundContext() != null ? "https://" : "http://";
        }
        String path = getKey().getPath() != null ? getKey().getPath() : "";
        int port = getKey().getPort();

        return "%s%s:%d%s%s".formatted(protocol, host, port, path, getSwaggerPath(api));
    }

    private String getSwaggerPath(OpenAPI api) {
        //noinspection OptionalGetWithoutIsPresent
        return "/api-docs/ui/" + apiProxy.apiRecords.entrySet().stream()
                .filter(d -> d.getValue().api == api)
                .findFirst()
                .get()
                .getKey();
    }


    private RuleKey getKey() {
        return router.getParentProxy(this).getKey();
    }

    private String buildValidationPropertiesDescription(Map<String, Object> props) {
        return """
            - Security: %s<br />
            - Requests: %s<br />
            - Responses: %s<br />
            - Details: %s<br />
            For in-depth explanation of these properties visit <a href="https://www.membrane-api.io/openapi/configuration-and-validation/index.html#validation"> here </a><br />
            """.formatted(props.get("security"), props.get("requests"), props.get("responses"), props.get("details"));
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