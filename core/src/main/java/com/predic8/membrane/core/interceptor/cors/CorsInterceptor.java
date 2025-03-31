package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "corsInterceptor")
public class CorsInterceptor extends AbstractInterceptor {

    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private boolean allowAll;
    private String allowOrigin;
    private String allowMethods;
    private String allowHeaders;
    private boolean allowCredentials;
    private int maxAge;

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (exc.getRequest().getHeader().getFirstValue("Origin") != null && "OPTIONS".equalsIgnoreCase(exc.getRequest().getMethod())) {
            Header header = Response.noContent().build().getHeader();
            header.add(ACCESS_CONTROL_ALLOW_ORIGIN, getAllowOrigin());
            header.add(ACCESS_CONTROL_ALLOW_METHODS, allowMethods);
            header.add(ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
            header.add(ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge));
            if (allowCredentials) {
                header.add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            }
            exc.setResponse(Response.noContent().header(header).build());
            return RETURN;
        }
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        Header header = exc.getResponse().getHeader();
        header.add(ACCESS_CONTROL_ALLOW_ORIGIN, getAllowOrigin());
        if (allowCredentials) {
            header.add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        return CONTINUE;
    }

    private String getAllowOrigin() {
        return allowAll ? "*" : allowOrigin;
    }

    @MCAttribute
    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
    }

    @MCAttribute
    public void setAllowOrigin(String allowOrigin) {
        this.allowOrigin = allowOrigin;
    }

    @MCAttribute
    public void setAllowMethods(String allowMethods) {
        this.allowMethods = allowMethods;
    }

    @MCAttribute
    public void setAllowHeaders(String allowHeaders) {
        this.allowHeaders = allowHeaders;
    }

    @MCAttribute
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    @MCAttribute
    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public String getAllowMethods() {
        return allowMethods;
    }

    public String getAllowHeaders() {
        return allowHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public int getMaxAge() {
        return maxAge;
    }
}
