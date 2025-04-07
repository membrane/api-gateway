package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.http.Response.noContent;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    public static final String ORIGIN = "Origin";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    private List<String> allowedOrigins;
    private List<String> methods;
    private String headers;
    private boolean credentials;
    private String maxAge;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().isOPTIONSRequest())
            return CONTINUE;

        String requestOrigin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        if (requestOrigin == null || isOriginAllowed(requestOrigin))
            return CONTINUE;

        exc.setResponse(noContent().header(createCORSHeader(new Header(), requestOrigin)).build());
        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String requestOrigin = exc.getRequest().getHeader().getFirstValue(ORIGIN);
        if (requestOrigin == null || isOriginAllowed(requestOrigin)) {
            return CONTINUE;
        }
        createCORSHeader(exc.getResponse().getHeader(), requestOrigin);
        return CONTINUE;
    }

    private boolean isOriginAllowed(String origin) {
        return !allowedOrigins.contains("*") && !allowedOrigins.contains(origin);
    }

    private Header createCORSHeader(Header header, String requestOrigin) {
        if (allowedOrigins.contains("*")) {
            if (credentials) {
                throw new ConfigurationException("UNSAFE CORS CONFIGURATION: 'credentials=true' and 'origins=*' is not allowed!");
            } else {
                header.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        } else {
            header.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, requestOrigin);
        }

        header.setValue(ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", methods));

        if (headers != null)
            header.setValue(ACCESS_CONTROL_ALLOW_HEADERS, headers);

        if (maxAge != null)
            header.setValue(ACCESS_CONTROL_MAX_AGE, maxAge);

        if (credentials && !"*".equals(requestOrigin))
            header.setValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        header.setValue("Vary", ORIGIN);
        return header;
    }

    @MCAttribute
    public void setOrigins(String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @MCAttribute
    public void setMethods(String methods) {
        this.methods = Arrays.stream(methods.split(","))
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
