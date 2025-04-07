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

    /**
     * Handles an incoming HTTP request to determine if it is a CORS preflight (OPTIONS) request.
     *
     * <p>If the request method is OPTIONS, the method constructs a no-content response with the appropriate CORS headers
     * and returns a termination outcome, halting further processing. Otherwise, it allows continued processing of the request.</p>
     *
     * @param exc the HTTP exchange containing the request and response objects
     * @return {@code RETURN} if the request is an OPTIONS preflight, {@code CONTINUE} for all other requests
     */
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


    /**
     * Retrieves the display name for the CORS interceptor.
     *
     * @return the display name "CORS"
     */
    @Override
    public String getDisplayName() {
        return "CORS";
    }

    /**
     * Populates the given header with CORS configuration based on the interceptor's settings.
     *
     * <p>If the interceptor is configured to allow all origins, the header is returned unmodified (pending further implementation).</p>
     *
     * <p>Otherwise, the header is updated with the allowed origin, comma-separated HTTP methods, permitted headers, preflight max age,
     * and, if credentials are enabled, a flag indicating support for credentials.</p>
     *
     * @param header the HTTP header to which CORS settings are added
     * @return the modified header with CORS-related entries
     */
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

    /**
     * Returns the allowed origin for CORS requests.
     *
     * <p>If the interceptor is configured to allow all origins, this method returns "*".
     * Otherwise, it returns the specific, configured origin.</p>
     *
     * @return "*" if all origins are permitted; otherwise, the configured origin.
     */
    private String getAllowOrigin() {
        return all ? "*" : origin;
    }

    /**
     * Adds a header entry if the provided value is not null.
     *
     * @param header the header to which the entry should be added
     * @param key the header key
     * @param val the header value to add if not null
     */
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
