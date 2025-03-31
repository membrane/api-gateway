package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "corsInterceptor")
public class CorsInterceptor extends AbstractInterceptor {

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
            header.add("Access-Control-Allow-Origin", getAllowOrigin());
            header.add("Access-Control-Allow-Methods", allowMethods);
            header.add("Access-Control-Allow-Headers", allowHeaders);
            header.add("Access-Control-Max-Age", String.valueOf(maxAge));
            if (allowCredentials) {
                header.add("Access-Control-Allow-Credentials", "true");
            }
            exc.setResponse(Response.noContent().header(header).build());
            return ABORT;
        }
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        if (exc.getRequest().getHeader().getFirstValue("Origin") != null) {
            Header header = exc.getResponse().getHeader();
            header.add("Access-Control-Allow-Origin", getAllowOrigin());
            if (allowCredentials) {
                header.add("Access-Control-Allow-Credentials", "true");
            }
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
}
