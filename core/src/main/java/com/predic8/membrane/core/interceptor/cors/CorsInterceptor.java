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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static java.util.Arrays.*;


/**
 * @description <p>Plugin that allows Cross-Origin Resource Sharing (CORS). It answers preflight
 * requests with the options method and sets the CORS headers. Additionally, requests
 * are validated against the CORS configuration.</p>
 *
 * <p>For a detailed explanation of CORS, see:</p>
 * <ul>
 *     <li><a href="https://www.membrane-api.io/cors-api-gateway.html" target="_blank">
 *         CORS Guide for API Developers
 *     </a></li>
 * </ul>
 * @topic 3. Security and Validation
 */
@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CorsInterceptor.class);

    // Request headers
    public static final String ORIGIN = "Origin";
    public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
    public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
    public static final String VARY_VALUE = ORIGIN + ", " + ACCESS_CONTROL_REQUEST_METHOD + ", " + ACCESS_CONTROL_REQUEST_HEADERS;

    // Response headers
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    public static final String NULL_STRING = "null";
    public static final String WILDCARD = "*";


    /**
     * If true, all origins, methods and headers are allowed **without validation**.
     * This is more permissive than setting origins/methods/headers to '*',
     * since it skips explicit validation checks. Not compatible with credentials=true.
     */
    private boolean allowAll = false;

    private Set<String> allowedOrigins = Set.of(WILDCARD);
    private Set<String> allowedMethods = Set.of(WILDCARD);
    private String allowedMethodsString;

    private Set<String> allowedHeaders = new HashSet<>();
    private boolean allowCredentials;
    private String maxAge;

    @Override
    public void init() {
        super.init();

        // See: https://fetch.spec.whatwg.org/#cors-protocol-and-credentials
        if (allowCredentials && isWildcardOriginAllowed()) {
            throw new ConfigurationException("Access-Control-Allow-Credentials in combination with origin wildcard is forbidden");
        }

       allowedMethodsString = join(allowedMethods.stream().toList());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (!exc.getRequest().isOPTIONSRequest())
            return CONTINUE; // no preflight -> let pass

        String origin = getNormalizedOrigin(exc);

        // Ordinary non CORS OPTIONS request -> let pass
        if (origin == null)
            return CONTINUE;

        String requestMethod = getRequestMethod(exc);

        if (allowAll) {
            exc.setResponse(noContent().build());
            setCORSHeader(exc, origin, requestMethod);
            return RETURN;
        }

        if (!originAllowed(origin)) {
            return createProblemDetails(exc, origin, "origin");
        }

        if (!methodAllowed(requestMethod)) {
            return createProblemDetails(exc, origin, "method");
        }

        String requestHeaders = getRequestHeaders(exc);
        if (!headersAllowed(requestHeaders)) {
            return createProblemDetails(exc, origin, "headers");
        }

        if (isWildcardOriginAllowed() && allowCredentials) {
            return createProblemDetails(exc, origin, "credentials");
        }

        exc.setResponse(noContent().build());
        setCORSHeader(exc, origin, requestMethod);
        return RETURN;
    }

    private static String getRequestHeaders(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_HEADERS);
    }

    private static String getRequestMethod(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(ACCESS_CONTROL_REQUEST_METHOD);
    }

    private static String getNormalizedOrigin(Exchange exc) {
        return CorsUtil.normalizeOrigin(exc.getRequest().getHeader().getFirstValue(ORIGIN));
    }

    private static Outcome createProblemDetails(Exchange exc, String origin, String type) {
        security(false, "cors")
                .statusCode(403)
                .addSubType("%s-not-allowed".formatted(type))
                .detail("The %s '%s' is not allowed by the CORS policy.".formatted(type, origin))
                .topLevel("origin", origin)
                .buildAndSetResponse(exc);
        log.info("CORS request denied: type={}, origin={}", type, origin);
        return RETURN;
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        String origin = getNormalizedOrigin(exc);
        if (origin == null)
            return CONTINUE;

        if (allowAll) {
            setCORSHeader(exc, origin, exc.getRequest().getMethod());
            return CONTINUE;
        }

        if (originAllowed(origin)) {
            setCORSHeader(exc, origin, exc.getRequest().getMethod());
        }

        // Not allowed => Do not set any allow headers
        return CONTINUE;
    }

    private boolean originAllowed(String origin) {
        if (NULL_STRING.equals(origin)) {
            return allowedOrigins.contains(NULL_STRING);
        }
        return isWildcardOriginAllowed() || allowedOrigins.contains(origin);
    }

    private boolean methodAllowed(String method) {
        return method != null && (allowedMethods.contains(method) || allowedMethods.contains(WILDCARD));
    }

    private boolean headersAllowed(String headers) {
        if (headers == null)
            return true;
        if (allowedHeaders.isEmpty()) return true; // Early exit for efficiency

        return allowedHeaders.containsAll(toLowerCaseSet(parseCommaOrSpaceSeparated(headers)));
    }

    private void setCORSHeader(Exchange exc, String requestOrigin, String requestedMethod) {
        Header responseHeader = exc.getResponse().getHeader();
        String requestedHeaders = getRequestHeaders(exc);

        responseHeader.setValue(ACCESS_CONTROL_ALLOW_ORIGIN, determineAllowOriginHeader(requestOrigin));
        responseHeader.setValue(ACCESS_CONTROL_ALLOW_METHODS, getAllowedMethods(requestedMethod));

        if (allowAll) {
            responseHeader.setValue(ACCESS_CONTROL_ALLOW_HEADERS,
                    requestedHeaders != null ? requestedHeaders.toLowerCase() : "content-type, authorization");
        } else if (!allowedHeaders.isEmpty()) {
            responseHeader.setValue(ACCESS_CONTROL_ALLOW_HEADERS,
                    join( List.copyOf(allowedHeaders)));
        }

        if (maxAge != null) {
            responseHeader.setValue(ACCESS_CONTROL_MAX_AGE, maxAge);
        }

        if (allowCredentials) {
            responseHeader.setValue(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        responseHeader.setValue(VARY, VARY_VALUE);
    }

    private @NotNull String getAllowedMethods(String requestedMethod) {
        return requestedMethod != null ? requestedMethod : allowedMethodsString;
    }

    private String determineAllowOriginHeader(String requestOrigin) {
        return (isWildcardOriginAllowed() && !allowCredentials) ? WILDCARD : requestOrigin;
    }

    private @NotNull String join(List<String> l) {
        return String.join(", ", l);
    }

    private boolean isWildcardOriginAllowed() {
        return allowedOrigins.contains(WILDCARD);
    }

    /**
     * If true, all origins, methods, and headers are allowed except credentials like cookies
     *
     * @description Allows all origins, methods, and headers without validation.
     * Not compatible with credentials=true.
     * @default false
     */
    @MCAttribute
    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
    }

    /**
     * @description Space-separated list of allowed origins. Use '*' to allow all.
     * @default *
     * @example https://example.com https://my.app
     */
    @MCAttribute
    public void setOrigins(String origins) {
        this.allowedOrigins = stream(origins.split(" "))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    /**
     * @description Comma-separated list of allowed HTTP methods.
     * @default *
     * @example GET, POST, PUT
     */
    @MCAttribute
    public void setMethods(String methods) {
        this.allowedMethods = parseCommaOrSpaceSeparated(methods);
    }

    /**
     * @description Comma-separated list of allowed request headers.
     * @example X-Custom-Header, Authorization, Content-Type
     */
    @MCAttribute
    public void setHeaders(String headers) {
        this.allowedHeaders = toLowerCaseSet( parseCommaOrSpaceSeparated(headers));
    }

    /**
     * @description Whether credentials like cookies or HTTP auth are allowed.
     * @default false
     */
    @MCAttribute
    public void setCredentials(boolean credentials) {
        this.allowCredentials = credentials;
    }

    /**
     * @description Max age (in seconds) for caching preflight responses.
     */
    @MCAttribute
    public void setMaxAge(String maxAge) {
        this.maxAge = maxAge;
    }


    public boolean isAllowAll() {
        return allowAll;
    }

    /**
     * For tests
     */
    protected Set<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * For tests
     */
    protected Set<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public String getHeaders() {
        return join(List.copyOf(allowedHeaders));
    }

    public Set<String> getMethods() {
        return allowedMethods;
    }

    public boolean isCredentials() {
        return allowCredentials;
    }

    public String getMaxAge() {
        return maxAge;
    }

    @Override
    public String getDisplayName() {
        return "CORS";
    }
}