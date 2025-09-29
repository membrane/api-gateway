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

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.cors.AbstractCORSHandler.*;
import static com.predic8.membrane.core.interceptor.cors.CorsUtil.*;
import static com.predic8.membrane.core.util.UrlNormalizer.*;

/**
 * @description Cross-Origin Resource Sharing (CORS) plugin that enables secure cross-origin HTTP requests.
 *
 * <p>This plugin handles CORS preflight requests (OPTIONS method) and sets appropriate CORS headers
 * on responses. It validates incoming requests against the configured CORS policy to ensure compliance
 * with the same-origin policy while allowing legitimate cross-origin access.</p>
 *
 * <h3>CORS Headers Handled</h3>
 * <ul>
 *   <li><strong>Request Headers:</strong> Origin, Access-Control-Request-Method, Access-Control-Request-Headers</li>
 *   <li><strong>Response Headers:</strong> Access-Control-Allow-Origin, Access-Control-Allow-Methods,
 *       Access-Control-Allow-Headers, Access-Control-Expose-Headers, Access-Control-Allow-Credentials,
 *       Access-Control-Max-Age</li>
 * </ul>
 *
 * <h3>Safe Headers</h3>
 * <p>The following headers are considered safe and don't require explicit configuration:</p>
 * <ul>
 *   <li>Accept</li>
 *   <li>Accept-Language</li>
 *   <li>Content-Language</li>
 *   <li>Content-Type (with certain MIME type restrictions)</li>
 * </ul>
 *
 * <h3>Configuration Examples</h3>
 *
 * <p><strong>Basic Configuration (Allow all origins):</strong></p>
 * <pre><code><cors />
 * </code></pre>
 *
 * <p><strong>Restrictive Configuration:</strong></p>
 * <pre><code><cors origins="https://example.com https://app.example.com" methods="GET,POST,PUT"
 *   headers="Authorization,X-Custom-Header"
 *   credentials="true"
 *   maxAge="3600" /></code></pre>
 *
 * <p><strong>Permissive Configuration:</strong></p>
 * <pre><code><cors allowAll="true" />
 * </code></pre>
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>When <code>credentials="true"</code>, wildcard origins (*) are forbidden for security reasons</li>
 *   <li>Always specify explicit origins in production when possible</li>
 * </ul>
 *
 * <h3>Standards Compliance</h3>
 * <p>This implementation follows the
 * <a href="https://fetch.spec.whatwg.org/#cors-preflight-fetch">Fetch Living Standard by WHATWG</a>.</p>
 *
 * <p>see: <a href="https://www.membrane-api.io/cors-api-gateway.html">CORS Guide for API Developers</a></p>
 * <p>see: <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS">MDN CORS Documentation</a></p>
 *
 * @author predic8 GmbH
 * @topic 3. Security and Validation
 */
@MCElement(name = "cors")
public class CorsInterceptor extends AbstractInterceptor {

    /**
     * Implementation Notes:
     * - There is no Access-Control-Request-Credentials header in the spec!
     */

    private static final Logger log = LoggerFactory.getLogger(CorsInterceptor.class);

    public static final String WILDCARD = "*";

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

    /** Maximum age in seconds for caching preflight responses. Default from CORS specification */
    private int maxAge = 5;

    private PreflightHandler preflightHandler;

    /** Handler for adding CORS headers to responses */
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
        if (!exc.getRequest().isOPTIONSRequest())
            return CONTINUE; // no preflight -> let pass
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
     * @description Enables or disables the "allow all" mode for maximum permissiveness.
     *
     * <p>When enabled, all origins, methods, and headers are allowed without any validation.
     * This bypasses all CORS security checks and should only be used in development environments.</p>
     *
     * <p><strong>Security Warning:</strong> This option is not compatible with credentials=true
     * and should never be used in production environments.</p>
     *
     * @param allowAll true to allow all requests without validation, false for normal CORS behavior
     * @default false
     *
     * @example
     * <pre><code><cors allowAll="true" /></code></pre>
     */
    @MCAttribute
    public void setAllowAll(boolean allowAll) {
        this.allowAll = allowAll;
    }

    /**
     * @throws ConfigurationException if an origin URL is malformed
     * @description Configures the list of allowed origins for CORS requests.
     *
     * <p>Origins must be specified as complete URLs including protocol (http/https).
     * Use '*' to allow all origins, or 'null' to allow requests with no origin header (from file).</p>
     *
     * @param origins space-separated list of allowed origins
     * @default "*" (allow all origins)
     *
     * @example
     * <pre><code><cors origins="https://example.com https://app.example.com null" /></code></pre>
     *
     */
    @MCAttribute
    public void setOrigins(String origins) {
        try {
            Set<String> set = new HashSet<>();
            for (String origin : splitBySpace(origins)) {
                switch (origin) {
                    case WILDCARD: set.add(WILDCARD); break;
                    case NULL_STRING: set.add(NULL_STRING); break;
                    default: {
                        try {
                            set.add(normalizeBaseUrl(origin));
                        } catch (Exception e) {
                            String msg = "Illegal value for cors origin: %s. Use http://..., https://... or null".formatted(origin);
                            log.error(msg);
                            throw new ConfigurationException(msg);
                        }
                    }
                }
            }
            this.allowedOrigins = set;
        } catch (Exception e) {
            log.error("Failed to parse origins list: {}", origins);
            log.error(e.getMessage(), e);
        }
    }

    public String getOrigins() {
        return String.join(SPACE, allowedOrigins);
    }


    /**
     * @description Configures the HTTP methods allowed for CORS requests.
     *
     * <p>Specify methods as a comma or space-separated list. Use '*' to allow all methods.
     * Common methods include GET, POST, PUT, DELETE, PATCH.</p>
     *
     * @param methods comma or space-separated list of HTTP methods
     * @default "*" (allow all methods)
     *
     * @example
     * <pre><code><cors methods="GET,POST,PUT,DELETE" /></code></pre>
     */
    @MCAttribute
    public void setMethods(String methods) {
        this.allowedMethods = StringList.parseToSet(methods);
    }

    public Set<String> getMethods() {
        return allowedMethods;
    }

    /**
     * @description Configures additional request headers allowed in CORS requests.
     *
     * <p>Safe headers (Accept, Accept-Language, Content-Language, Content-Type) are always allowed
     * and don't need to be specified. Only non-safe headers need to be explicitly configured.</p>
     *
     * <p>Header names are case-insensitive and will be normalized to lowercase internally.</p>
     *
     * @param headers comma or space-separated list of header names
     * @default "" (no additional headers beyond safe headers)
     *
     * @example
     * <pre><code><cors headers="Authorization,X-Custom-Header,X-Requested-With" /></code></pre>
     */
    @MCAttribute
    public void setHeaders(String headers) {
        this.allowedHeaders = CollectionsUtil.toLowerCaseSet( StringList.parseToSet(headers));
    }

    public String getHeaders() {
        return CollectionsUtil.join(List.copyOf(allowedHeaders));
    }

    /**
     * @description Configures response headers that should be exposed to client-side JavaScript.
     *
     * <p>By default, only safe response headers are exposed to JavaScript. Use this setting
     * to expose additional custom headers that your client-side code needs to access.</p>
     *
     * <p>Header names are case-insensitive and will be normalized to lowercase internally.</p>
     *
     * @param headers comma or space-separated list of header names to expose
     * @default "" (expose no additional headers)
     *
     * @example
     * <pre><code><cors exposeHeaders="X-Total-Count,X-Custom-Info" /></code></pre>
     */
    @MCAttribute
    public void setExposeHeaders(String headers) {
        this.exposeHeaders = CollectionsUtil.toLowerCaseSet( StringList.parseToSet(headers));
    }

    public String getExposeHeaders() {
        return CollectionsUtil.join(List.copyOf(exposeHeaders));
    }

    /**
     * @description Configures whether credentials should be included in CORS requests.
     *
     * <p>When enabled, browsers will include cookies, authorization headers, and client certificates
     * in cross-origin requests. This also requires the client to set <code>withCredentials: true</code>
     * in their request configuration.</p>
     *
     * <p><strong>Security Restriction:</strong> Cannot be used with wildcard origins (*) due to security risks.</p>
     *
     * @param credentials true to allow credentials, false to disallow
     * @default false
     *
     * @example
     * <pre><code><cors
     *   origins="https://trusted-app.com"
     *   credentials="true" /></code></pre>
     */
    @MCAttribute
    public void setCredentials(boolean credentials) {
        this.allowCredentials = credentials;
    }

    public boolean getCredentials() {
        return allowCredentials;
    }

    /**
     * @description Configures the maximum age for caching preflight responses.
     *
     * <p>This value tells browsers how long they can cache the result of a preflight request
     * before making another preflight request for the same resource.</p>
     *
     * <p>Setting this to a higher value reduces the number of preflight requests but may
     * delay the effect of CORS configuration changes.</p>
     *
     * @param maxAge maximum cache age in seconds (0 = no caching)
     * @default 5 seconds
     *
     * @example
     * <pre><code><cors maxAge="3600" /></code></pre>
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