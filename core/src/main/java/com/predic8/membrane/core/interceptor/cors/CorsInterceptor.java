package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.http.Response.noContent;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;



/**
 * @description
 * <p>A Plugin for handling Cross-Origin Resource Sharing (CORS) requests.
 * It allows control over which origins, methods, and headers are permitted
 * during cross-origin requests.</p>
 */
@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    public static final String ORIGIN = "Origin";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
    public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";


    private List<String> allowedOrigins;
    private List<String> methods;
    private String headers;
    private boolean credentials;
    private String maxAge;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().isOPTIONSRequest())
            return CONTINUE; // no preflight -> let pass

        String requestOrigin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        String requestMethod = exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_METHOD);
        String requestHeaders = exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_HEADERS);

        if (requestOrigin == null)
            return CONTINUE;

        if (!isOriginAllowed(requestOrigin)) {
            return getProblemDetails(exc, requestOrigin, "origin");
        }

        if (!isMethodAllowed(requestMethod)) {
            return getProblemDetails(exc, requestOrigin, "method");
        }

        if (!areHeadersAllowed(requestHeaders)) {
            return getProblemDetails(exc, requestOrigin, "headers");
        }

        exc.setResponse(noContent().header(createCORSHeader(new Header(), requestOrigin,  requestMethod)).build());
        return RETURN;
    }

    private static Outcome getProblemDetails(Exchange exc, String requestOrigin, String type) {
        security(false, "cors-interceptor")
                .statusCode(403)
                .addSubType("%s-not-allowed".formatted(type))
                .detail("The %s '%s' is not allowed by the CORS policy.".formatted(type, requestOrigin))
                .topLevel("origin", requestOrigin)
                .buildAndSetResponse(exc);
        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String requestOrigin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        if (requestOrigin == null)
            return CONTINUE;

        if (isOriginAllowed(requestOrigin)) {
            createCORSHeader(exc.getResponse().getHeader(), requestOrigin, exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_HEADERS));
        }

        return CONTINUE;
    }


    private boolean isOriginAllowed(String origin) {
        if ("null".equals(origin)) {
            return allowedOrigins.contains("null");
        }

        return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
    }

    private boolean isMethodAllowed(String method) {
        return method != null && methods.contains(method);
    }

    private boolean areHeadersAllowed(String requestedHeaders) {
        if (requestedHeaders == null || headers == null)
            return true;

        List<String> requested = Arrays.stream(requestedHeaders.split(","))
                .map(h -> h.trim().toLowerCase())
                .toList();

        List<String> allowed = Arrays.stream(headers.split(","))
                .map(h -> h.trim().toLowerCase())
                .toList();

        return new HashSet<>(allowed).containsAll(requested);
    }


    private Header createCORSHeader(Header header, String requestOrigin, String requestedMethod) {
        if (allowedOrigins.contains("*")) {
            if (credentials) {
                throw new ConfigurationException("UNSAFE CORS CONFIGURATION: 'credentials=true' and 'origins=*' is not allowed!");
            }
            header.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, requestOrigin);
        } else {
            header.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, requestOrigin);
        }

        header.setValue(ACCESS_CONTROL_ALLOW_METHODS, requestedMethod);

        if (headers != null) {
            header.setValue(ACCESS_CONTROL_ALLOW_HEADERS, headers);
        }

        if (maxAge != null) {
            header.setValue(ACCESS_CONTROL_MAX_AGE, maxAge);
        }

        if (credentials) {
            header.setValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        header.setValue("Vary", ORIGIN);
        return header;
    }

    @MCAttribute
    @Required
    public void setOrigins(String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(" "))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @MCAttribute
    @Required
    public void setMethods(String methods) {
        this.methods = Arrays.stream(methods.split(", "))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @MCAttribute
    public void setHeaders(String headers) {
        this.headers = headers;
    }

    @MCAttribute
    public void setCredentials(boolean credentials) {
        this.credentials = credentials;
    }

    @MCAttribute
    public void setMaxAge(String maxAge) {
        this.maxAge = maxAge;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public List<String> getMethods() {
        return methods;
    }

    public String getHeaders() {
        return headers;
    }

    public boolean isCredentials() {
        return credentials;
    }

    public String getMaxAge() {
        return maxAge;
    }

    @Override
    public String getDisplayName() {
        return "CORS";
    }
}
