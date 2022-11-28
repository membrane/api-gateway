package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIProxy.*;
import com.predic8.membrane.core.openapi.validators.*;
import io.swagger.v3.oas.models.*;
import redis.clients.jedis.*;

import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.SNI_SERVER_NAME;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.openapi.util.PathUtils.getUrlWithoutPath;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorRequest;
import static com.predic8.membrane.core.openapi.util.Utils.getOpenapiValidatorResponse;


public class OpenAPIInterceptor extends AbstractInterceptor {

    Map<String, OpenAPI> apisByBasePath;

    public OpenAPIInterceptor(Map<String, OpenAPI> apisByBasePath) {
        this.apisByBasePath = apisByBasePath;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        exc.getDestinations().clear();

        ValidationErrors errors = null;
        for (String basePath : apisByBasePath.keySet()) {
            if (exc.getRequest().getUri().startsWith(basePath)) {
                OpenAPI api = apisByBasePath.get(basePath);
                errors = validateRequest(api, exc);
                addDestinationsFromOpenAPIServers(api, exc, basePath);
            }
        }

        if (errors != null && errors.size() > 0)
            return returnErrors(exc, errors);

        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        ValidationErrors errors = null;
        for (String basePath : apisByBasePath.keySet()) {
            if (exc.getRequest().getUri().startsWith(basePath)) {
                OpenAPI api = apisByBasePath.get(basePath);
                errors = validateResponse(api, exc);
            }
        }

        if (errors != null && errors.size() > 0)
            return returnErrors(exc, errors);

        return CONTINUE;
    }

    private ValidationErrors validateRequest(OpenAPI api, Exchange exc) throws IOException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(api, VALIDATE_OPTIONS.requests.name()))
            return errors;

        return new OpenAPIValidator(api).validate(getOpenapiValidatorRequest(exc));
    }

    private ValidationErrors validateResponse(OpenAPI api, Exchange exc) throws IOException {
        ValidationErrors errors = new ValidationErrors();
        if (!shouldValidate(api, VALIDATE_OPTIONS.responses.name()))
            return errors;
        return new OpenAPIValidator(api).validateResponse(getOpenapiValidatorRequest(exc), getOpenapiValidatorResponse(exc));
    }

    private boolean shouldValidate(OpenAPI api, String direction) {
        Map<String, Object> extenstions = api.getExtensions();
        if (extenstions == null)
            return false;
        Map<String, Boolean> xValidation = getxValidation(extenstions);
        return xValidation != null && xValidation.get(VALIDATE_OPTIONS.requests.name());
    }

    private Map<String, Boolean> getxValidation(Map<String, Object> extenstions) {
        return (Map<String, Boolean>) extenstions.get("x-validation");
    }

    private void addDestinationsFromOpenAPIServers(OpenAPI api, Exchange exc, String basePath) {
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

            // @TODO add dispatcherInterceptor SSL Setting

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

        for (Map.Entry<String, OpenAPI> entry : apisByBasePath.entrySet()) {
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(entry.getValue().getInfo().getTitle());
            sb.append("</td>");
            sb.append("<td>");
            sb.append(entry.getKey());
            sb.append("</td>");
            sb.append("<td>");
            if (entry.getValue().getExtensions() != null && entry.getValue().getExtensions().get("x-validation") != null) {
                sb.append(entry.getValue().getExtensions().get("x-validation"));
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


    private Outcome returnErrors(Exchange exc, ValidationErrors errors) {
        exc.setResponse(com.predic8.membrane.core.http.Response.ResponseBuilder.newInstance().status(400, "Bad Request").body(errors.toString()).contentType(APPLICATION_JSON_UTF8).build());
        return RETURN;
    }


}
