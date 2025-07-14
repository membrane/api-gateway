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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static java.util.Arrays.*;


//
// 2. Safe-listed headers not explicitly handled
// The spec defines certain headers as always allowed (Accept, Accept-Language, Content-Language, and Content-Type with specific values), but the implementation doesn't explicitly handle these. When no headers are configured, it appears to reject all non-configured headers rather than allowing the safe-listed ones.
//
// Returning only requested method? What is right?


/**
 * @description <p>Plugin that allows Cross-Origin Resource Sharing (CORS). It answers preflight
 * requests with the options method and sets CORS headers. Additionally, requests
 * are validated against the CORS configuration.</p>
 *
 * The following headers are regarded save: Accept, Accept-Language, Content-Language, Content-Type
 *
 * The plugin follow the <a href="https://fetch.spec.whatwg.org/#cors-preflight-fetch">Fetch, Living Standard by WhatWG</a>
 *
 * <p>For a detailed explanation of CORS, see:</p>
 * <ul>
 *     <li><a href="https://www.membrane-api.io/cors-api-gateway.html" target="_blank">
 *         CORS Guide for API Developers
 *     </a></li>
 * </ul>
 *
 * @topic 3. Security and Validation
 */
@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    /**
     * Implementation Notes:
     * - There is no Access-Control-Request-Credentials header in the spec!
     *
     */

    private static final Logger log = LoggerFactory.getLogger(CorsInterceptor.class);

    public static final String WILDCARD = "*";
    public static final String SPACE = " ";

    /**
     * If true, all origins, methods and headers are allowed **without validation**.
     * This is more permissive than setting origins/methods/headers to '*',
     * since it skips explicit validation checks. Not compatible with credentials=true.
     */
    private boolean allowAll = false;

    private Set<String> allowedOrigins = Set.of(WILDCARD);
    private Set<String> allowedMethods = Set.of(WILDCARD);

    private Set<String> allowedHeaders = Collections.emptySet();
    private Set<String> exposeHeaders = new HashSet<>();
    private boolean allowCredentials;

    // Default from https://fetch.spec.whatwg.org/#cors-preflight-fetch
    private int maxAge = 5;

    private PreflightHandler preflightHandler;
    private ResponseHandler responseHandler;

    @Override
    public void init() {
        super.init();

        // See: https://fetch.spec.whatwg.org/#cors-protocol-and-credentials
        if (allowCredentials && isWildcardOriginAllowed()) {
            throw new ConfigurationException("Access-Control-Allow-Credentials in combination with origin wildcard is forbidden");
        }

        preflightHandler = new PreflightHandler(this);
        responseHandler = new ResponseHandler(this);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return preflightHandler.handle(exc);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return responseHandler.handle(exc);
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
     * @default * Allow all origins
     * @example https://example.com https://my.app
     */
    @MCAttribute
    public void setOrigins(String origins) {
        this.allowedOrigins = stream(origins.split(SPACE))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    public String getOrigins() {
        return String.join(SPACE, allowedOrigins);
    }



    /**
     * @description Comma-separated list of allowed HTTP methods.
     * @default GET
     * @example GET, POST, PUT
     */
    @MCAttribute
    public void setMethods(String methods) {
        this.allowedMethods = parseCommaOrSpaceSeparated(methods);
    }

    public Set<String> getMethods() {
        return allowedMethods;
    }

    /**
     * @description Comma-separated list of allowed request headers.
     * Content-Type and Content-Length
     *
     * @example X-Custom-Header, Authorization, Content-Type
     * @default No headers besides the save headers listed above
     */
    @MCAttribute
    public void setHeaders(String headers) {
        this.allowedHeaders = toLowerCaseSet( parseCommaOrSpaceSeparated(headers));
    }

    public String getHeaders() {
        return join(List.copyOf(allowedHeaders));
    }

    @MCAttribute
    public void setExposeHeaders(String headers) {
        this.allowedHeaders = toLowerCaseSet( parseCommaOrSpaceSeparated(headers));
    }

    public String getExposeHeaders() {
        return join(List.copyOf(exposeHeaders));
    }

    /**
     * @description Whether credentials like cookies or HTTP auth are allowed.
     * @default false
     */
    @MCAttribute
    public void setCredentials(boolean credentials) {
        this.allowCredentials = credentials;
    }

    public boolean getCredentials() {
        return allowCredentials;
    }

    /**
     * @description Max age (in seconds) for caching preflight responses.
     * @default 0 Which means do not cache
     */
    @MCAttribute
    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public int getMaxAge() {
        return maxAge;
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


    @Override
    public String getDisplayName() {
        return "CORS";
    }
}