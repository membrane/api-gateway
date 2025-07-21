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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.COOKIE;
import static com.predic8.membrane.core.http.Header.ORIGIN;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.AbstractCORSHandler.ResponseHeaderBuilder.responseBuilder;
import static com.predic8.membrane.core.interceptor.cors.CorsInterceptor.*;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.*;

public abstract class AbstractCORSHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractCORSHandler.class);

    /**
     * Only include origin in vary header (See fetch spec)
     */
    public static final String VARY_VALUE = ORIGIN;

    public static final String NULL_STRING = "null";

    protected final CorsInterceptor interceptor;

    private final String allowedMethodsString;

    public AbstractCORSHandler(CorsInterceptor interceptor) {
        this.interceptor = interceptor;
        allowedMethodsString = join(interceptor.getMethods().stream().toList());
    }

    protected abstract Outcome handleInternal(Exchange exc, String origin);

    protected abstract String getRequestMethod(Exchange exc);

    public Outcome handle(Exchange exc) {
        String origin = getNormalizedOrigin(exc);

        // Ordinary non CORS request -> let pass
        if (origin == null)
            return CONTINUE;

        return handleInternal(exc, origin);
    }

    protected boolean originAllowed(String origin) {
        if (NULL_STRING.equals(origin)) {
            return interceptor.getAllowedOrigins().contains(NULL_STRING);
        }
        return isWildcardOriginAllowed() || interceptor.getAllowedOrigins().contains(origin);
    }

    protected boolean isWildcardOriginAllowed() {
        return interceptor.getAllowedOrigins().contains(WILDCARD);
    }

    protected Outcome createProblemDetails(Exchange exc, String origin, String type) {
        security(false, "cors")
                .statusCode(403)
                .addSubType("%s-not-allowed".formatted(type))
                .detail("The %s '%s' is not allowed by the CORS policy.".formatted(type, origin))
                .topLevel("origin", origin)
                .buildAndSetResponse(exc);
        log.info("CORS request denied: type={}, origin={}", type, origin);
        return RETURN;
    }

    protected static String getAccessControlRequestHeaderValue(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_HEADERS);
    }


    protected @NotNull String join(List<String> l) {
        return String.join(", ", l);
    }

    @NotNull String getAllowedMethods(String requestedMethod) {
        return requestedMethod != null ? requestedMethod : allowedMethodsString;
    }

    protected void setCORSHeader(Exchange exc, String requestOrigin) {
        responseBuilder(exc)
                .allowOrigin(determineAllowOriginHeader(requestOrigin))
                .allowMethods(getAllowedMethods(getRequestMethod(exc)))
                .allowHeaders(getAllowHeaders(getAccessControlRequestHeaderValue(exc)))
                .exposeHeaders(interceptor.getExposeHeaders())
                .maxAge(interceptor.getMaxAge())
                .allowCredentials(interceptor.getCredentials())
                .build();
    }

    static class ResponseHeaderBuilder {
        Header responseHeader;
        Header requestHeader;
        Exchange exchange;

        static ResponseHeaderBuilder responseBuilder(Exchange exchange) {
            ResponseHeaderBuilder builder = new ResponseHeaderBuilder();
            builder.responseHeader = exchange.getResponse().getHeader();
            builder.requestHeader = exchange.getRequest().getHeader();
            builder.exchange = exchange;
            return builder;
        }

        ResponseHeaderBuilder allowOrigin(String origin) {
            responseHeader.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            return this;
        }

        ResponseHeaderBuilder allowMethods(String allowedMethods) {
            if (requestHeader.contains(ACCESS_CONTROL_REQUEST_METHOD)) {
                responseHeader.setValue(ACCESS_CONTROL_ALLOW_METHODS, allowedMethods);
            }
            return this;
        }

        ResponseHeaderBuilder allowHeaders(String allowedHeaders) {
            if (requestHeader.contains(ACCESS_CONTROL_REQUEST_HEADERS)) {
                responseHeader.setValue(ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders);
            }
            return this;
        }

        ResponseHeaderBuilder exposeHeaders(String exposeHeaders) {
            if (exposeHeaders.isEmpty())
                return this;

            responseHeader.setValue(ACCESS_CONTROL_EXPOSE_HEADERS, exposeHeaders);
            return this;
        }

        ResponseHeaderBuilder maxAge(int maxAge) {
            responseHeader.setValue(ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge));
            return this;
        }

        ResponseHeaderBuilder allowCredentials(boolean allowCredentials) {
            if (credentialsNeeded(requestHeader)) {
                responseHeader.setValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(allowCredentials));
            }
            return this;
        }

        Header build() {
            responseHeader.setValue(VARY, VARY_VALUE);
            return responseHeader;
        }

    }

    private static boolean credentialsNeeded(Header header) {
        if (header.contains(COOKIE)) {
            return true;
        }
        if (header.contains(Header.AUTHORIZATION)) {
            return true;
        }
        return false;
    }

    private String determineAllowOriginHeader(String requestOrigin) {
        // We do not Just Echo Every Origin Blindly
        // If we automatically echo back any origin without validation:
        // we would  bypassing origin restrictions, which can be a security risk.
        // Returning the wildcard when allowCredentials is false is on purpose!
        return (isWildcardOriginAllowed() && !interceptor.getCredentials()) ? WILDCARD : requestOrigin;
    }

    private String getAllowHeaders(String requestedHeaders) {
        if (interceptor.isAllowAll()) {
            return requestedHeaders != null ? requestedHeaders.toLowerCase() : "content-type, authorization";
        }
        if (!interceptor.getAllowedHeaders().isEmpty()) {
            return join(List.copyOf(interceptor.getAllowedHeaders()));
        }
        return ""; // Todo Check
    }
}