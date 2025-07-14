package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static org.springframework.http.HttpHeaders.*;

public class PreflightHandler extends AbstractCORSHandler {

    /**
     * From https://fetch.spec.whatwg.org/#terminology-headers
     */
    public static final Set<String> SAFE_HEADERS = Set.of(
                "accept",
                "accept-language",
                "content-language",
                "content-type",
                "range"
                );
    private static final Logger log = LoggerFactory.getLogger(PreflightHandler.class);

    public PreflightHandler(CorsInterceptor interceptor) {
        super(interceptor);
    }

    public Outcome handleInternal(Exchange exc, String origin) {
        if (!exc.getRequest().isOPTIONSRequest())
            return CONTINUE; // no preflight -> let pass

        if (interceptor.isAllowAll()) {
            exc.setResponse(noContent().build());
            setCORSHeader(exc, origin);
            return RETURN;
        }

        if (!originAllowed(origin)) {
            return createProblemDetails(exc, origin, "origin");
        }

        if (!methodAllowed(exc)) {
            return createProblemDetails(exc, origin, "method");
        }

        if (!headersAllowed(getAccessControlRequestHeaderValue(exc))) {
            return createProblemDetails(exc, origin, "headers");
        }

        if (isWildcardOriginAllowed() && interceptor.getCredentials()) {
            return createProblemDetails(exc, origin, "credentials");
        }

        exc.setResponse(noContent().build());
        setCORSHeader(exc, origin);
        return RETURN;
    }

    /**
     * Validates whether the requested headers are allowed by the CORS policy.
     *
     * @param headers comma or space-separated list of header names from the
     *                Access-Control-Request-Headers header, or null if no headers were requested
     * @return true if all requested headers are allowed, false otherwise
     *
     */
    public boolean headersAllowed(String headers) {
        // There are no headers
        if (headers == null)
            return true;

        Set<String> allowedHeaders = interceptor.getAllowedHeaders();

        for(String header : toLowerCaseSet(parseCommaOrSpaceSeparated(headers))) {
            if (SAFE_HEADERS.contains(header))
                continue;
            if (allowedHeaders.contains(header))
                continue;
            log.debug("header '{}' not allowed!", header);
            return false;
        }
        return true;
    }

    /*

        private boolean methodAllowed(String method) {
        return method != null && (allowedMethods.contains(method) || allowedMethods.contains(WILDCARD));
    }
     */

    private boolean methodAllowed(Exchange exc) {
        String method = getRequestMethod(exc);
        return method != null && (interceptor.getMethods().contains(method) || (interceptor.getMethods().contains(WILDCARD)));
    }

    protected String getRequestMethod(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_METHOD);
    }
}
