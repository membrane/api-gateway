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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import jakarta.mail.internet.*;
import redis.clients.jedis.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.openapi.serviceproxy.APIProxy.*;
import static com.predic8.membrane.core.openapi.util.UriUtil.*;
import static com.predic8.membrane.core.openapi.util.Utils.*;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.REQUEST;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.Direction.RESPONSE;


public class OpenAPIInterceptor extends AbstractInterceptor {

    protected final APIProxy proxy;

    public OpenAPIInterceptor(APIProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String basePath = getMatchingBasePath(exc);
        // No matching API found
        if  (basePath == null) {
            exc.setResponse(Response.notFound().contentType(APPLICATION_JSON_UTF8).body(errorJson("No matching API found!")).build());
            return RETURN;
        }

        OpenAPI api = proxy.getBasePaths().get(basePath);

        // If OpenAPIProxy has a <target> Element use this for routing otherwise
        // take the urls from the info.servers field in the OpenAPI document.
        if (proxy.getTarget() == null || (proxy.getTarget().getHost() == null && proxy.getTarget().getUrl() == null))
            setDestinationsFromOpenAPI(api, exc);

        ValidationErrors errors = validateRequest(api, exc);

        if (errors != null && errors.size() > 0) {
            proxy.statisticCollector.collect(errors);
            return returnErrors(exc, errors, REQUEST, validationDetails(api));
        }

        exc.setProperty("openApi",api);

        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {

        OpenAPI api = (OpenAPI) exc.getProperty("openApi");
        ValidationErrors errors = validateResponse(api, exc);

        if (errors != null && errors.size() > 0) {
            exc.getResponse().setStatusCode(500); // A validation error in the response is a server error!
            proxy.statisticCollector.collect(errors);
            return returnErrors(exc, errors, RESPONSE, validationDetails(api));
        }
        return CONTINUE;
    }

    private byte[] errorJson(String msg) {
        return String.format("{ \"error\": \"%s\"}", msg).getBytes();
    }

    protected String getMatchingBasePath(Exchange exc) {
        for (String basePath : proxy.getBasePaths().keySet()) {
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
        Map<String,Object> xValidation = (Map<String, Object>) api.getExtensions().get(X_MEMBRANE_VALIDATION);

        if (xValidation == null)
            return true;

        Boolean validationDetails = (Boolean) xValidation.get("details");

        if (xValidation.get("details") == null)
            return true;

        return validationDetails;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean shouldValidate(OpenAPI api, String direction) {
        Map<String, Object> extenstions = api.getExtensions();
        if (extenstions == null)
            return false;
        Map<String, Boolean> xValidation = getxValidation(extenstions);
        return xValidation != null && xValidation.get(direction);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> getxValidation(Map<String, Object> extenstions) {
        return (Map<String, Boolean>) extenstions.get(X_MEMBRANE_VALIDATION);
    }

    protected void setDestinationsFromOpenAPI(OpenAPI api, Exchange exc) {
        exc.getDestinations().clear();
        api.getServers().forEach(server -> {
            URL url;
            try {
                url = new URL(server.getUrl());
            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new RuntimeException("Cannot parse server address from OpenAPI " + server.getUrl());
            }

            setHostHeader(exc, url);
            exc.setProperty(SNI_SERVER_NAME, url.getHost());
            exc.getDestinations().add(getUrlWithoutPath(url) + exc.getRequest().getUri());
        });
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

        for (Map.Entry<String, OpenAPI> entry : proxy.getBasePaths().entrySet()) {
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(entry.getValue().getInfo().getTitle());
            sb.append("</td>");
            sb.append("<td>");
            sb.append(entry.getKey());
            sb.append("</td>");
            sb.append("<td>");
            if (entry.getValue().getExtensions() != null && entry.getValue().getExtensions().get(X_MEMBRANE_VALIDATION) != null) {
                sb.append(entry.getValue().getExtensions().get(X_MEMBRANE_VALIDATION));
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
