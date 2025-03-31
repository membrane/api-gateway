package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private boolean all;
    private String origin;
    private String methods;
    private String headers;
    private boolean credentials;
    private String maxAge;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if ("OPTIONS".equalsIgnoreCase(exc.getRequest().getMethod())) {
            exc.setResponse(Response.noContent().header(createCORSHeader()).build());
           return RETURN;
        }
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        exc.getResponse().setHeader(createCORSHeader());
        return CONTINUE;
    }

    @Override
    public String getDisplayName() {
        return "CORS";
    }

    private Header createCORSHeader() {
        Header header = Response.noContent().build().getHeader();
        header.addIfPresent(ACCESS_CONTROL_ALLOW_ORIGIN, getAllowOrigin());
        header.addIfPresent(ACCESS_CONTROL_ALLOW_METHODS, methods);
        header.addIfPresent(ACCESS_CONTROL_ALLOW_HEADERS, headers);
        header.addIfPresent(ACCESS_CONTROL_MAX_AGE, maxAge);
        if (credentials) {
            header.addIfPresent(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        return header;
    }

    private String getAllowOrigin() {
        return all ? "*" : origin;
    }

    @MCAttribute
    public void setAll(boolean all) {
        this.all = all;
    }

    @MCAttribute
    public void setOrigin(String origin) {
        this.origin = origin;
    }

    @MCAttribute
    public void setMethods(String methods) {
        this.methods = methods;
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

    public boolean isAll() {
        return all;
    }

    public String getMethods() {
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
}
