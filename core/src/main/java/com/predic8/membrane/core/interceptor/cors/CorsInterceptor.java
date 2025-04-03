package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    public static final String ORIGIN = "Origin";
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private boolean all;
    private String origin;
    private List<String> methods;
    private String headers;
    private boolean credentials;
    private String maxAge;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (exc.getRequest().isOPTIONSRequest()) {
            exc.setResponse(noContent().header(createCORSHeader(new Header())).build());
           return RETURN;
        }
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        exc.getResponse().setHeader(createCORSHeader(exc.getResponse().getHeader()));
        return CONTINUE;
    }


    @Override
    public String getDisplayName() {
        return "CORS";
    }

    private Header createCORSHeader(Header header) {
        if (all) {
            // TODO
            return header;
        }

        // Match origin aga
        addIfPresent(header, ACCESS_CONTROL_ALLOW_ORIGIN, getAllowOrigin());

        addIfPresent(header, ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", getMethods()));
        addIfPresent(header, ACCESS_CONTROL_ALLOW_HEADERS, getHeaders());
        addIfPresent(header, ACCESS_CONTROL_MAX_AGE, getMaxAge());
        if (credentials) {
            addIfPresent(header, ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        return header;
    }

    private String getAllowOrigin() {
        return all ? "*" : origin;
    }

    private void addIfPresent(Header header, String key, String val) {
        if (val != null) {
            header.add(key, val);
        }
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

    public boolean isAll() {
        return all;
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
}
