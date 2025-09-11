/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.AbstractCORSHandler.ResponseHeaderBuilder.*;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static com.predic8.membrane.core.util.CollectionsUtil.*;
import static com.predic8.membrane.core.util.StringList.*;
import static org.springframework.http.HttpHeaders.*;

public class PreflightHandler extends AbstractCORSHandler {

    private static final Logger log = LoggerFactory.getLogger(PreflightHandler.class);

    /**
     * From <a href="https://fetch.spec.whatwg.org/#terminology-headers">fetch specification</a>
     */
    public static final Set<String> SAFE_HEADERS = Set.of(
                "accept",
                "accept-language",
                "content-language",
                "content-type",
                "range"
                );

    public PreflightHandler(CorsInterceptor interceptor) {
        super(interceptor);
    }

    public Outcome handleInternal(Exchange exc, String origin) {
        if (interceptor.isAllowAll()) {
            exc.setResponse(noContent().build());
            setCORSHeader(exc, origin);
            return RETURN;
        }

        if (!originAllowed(origin)) {
            return createProblemDetails(exc, origin, "origin");
        }

        if (!methodAllowed(exc)) {
            return createProblemDetails(exc, getRequestMethod(exc), "method");
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

        for(String header : toLowerCaseSet( parseToSet(headers))) {
            if (SAFE_HEADERS.contains(header))
                continue;
            if (interceptor.getAllowedHeaders().contains(header))
                continue;
            log.debug("header '{}' not allowed!", header);
            return false;
        }
        return true;
    }

    private boolean methodAllowed(Exchange exc) {
        String method = getRequestMethod(exc);
        return method != null && (interceptor.getMethods().contains(method) || (interceptor.getMethods().contains(WILDCARD)));
    }

    protected String getRequestMethod(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_METHOD);
    }

    protected void setCORSHeader(Exchange exc, String requestOrigin) {
        responseBuilder(exc)
                .allowOrigin(determineAllowOriginHeader(requestOrigin))
                .allowMethods(getAllowedMethods(getRequestMethod(exc)))
                .allowHeaders(getAllowHeaders(getAccessControlRequestHeaderValue(exc)))
                .maxAge(interceptor.getMaxAge())
                .allowCredentials(interceptor.getCredentials())
                .build();
    }

    private String getAllowHeaders(String requestedHeaders) {
        if (interceptor.isAllowAll()) {
            if (requestedHeaders == null || requestedHeaders.isBlank())
                return null;
            return requestedHeaders; // Best practice to reflect the requested headers
        }
        if (!interceptor.getAllowedHeaders().isEmpty()) {
            // The returned header need not to reflect the requested ones, it can
            // lead to unexpected behaviour!
            return join(List.copyOf(interceptor.getAllowedHeaders()));
        }
        return null;
    }
}
