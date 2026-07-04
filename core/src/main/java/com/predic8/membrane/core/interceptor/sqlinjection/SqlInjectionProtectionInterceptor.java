/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.sqlinjection;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.sqlinjection.SqlInjectionProtection.Detection;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description <p>Detects SQL injection patterns in incoming requests and blocks them before they reach the backend.
 * Inspects query parameters, the request path, form and JSON bodies (and optionally headers) against a set of
 * detection rules transpiled from the OWASP Core Rule Set (CRS) REQUEST-942 SQL-injection rules.</p>
 * <p>The rule set is bundled; no download or configuration is required. The <code>level</code> attribute selects
 * the CRS paranoia level (1 = aggressive, low false positives; 4 = paranoid). Set <code>onDetect</code> to
 * <code>warn</code> to log detections without blocking while tuning a deployment.</p>
 * <p>Requests and responses are both inspected: when reached in the request flow the request is checked, when
 * reached in the response flow the response is checked. Because Membrane can also act as an outbound
 * (egress/forward) gateway, inspecting responses guards untrusted upstreams and stops SQL error fragments
 * leaking back to clients. Scope it to one direction by placing it inside a <code>request</code> or
 * <code>response</code> flow element.</p>
 * <p>Scanning is not free: the message body is read into memory and matched against every active rule.
 * Cost grows with body size and paranoia level. Pair this plugin with a <code>limit</code> plugin placed
 * before it, so oversized bodies are rejected before they are buffered and scanned:</p>
 * <pre><code>
 * - limit:
 *     maxBodyLength: 100000
 * - sqlInjectionProtection:
 *     level: 1
 * </code></pre>
 * <p>This is a defense-in-depth filter, not a complete guarantee. Signature-based detection can be bypassed
 * and always carries a false-positive risk; it does not replace parameterized queries (prepared statements) in
 * the backend, which remain the primary protection against SQL injection.</p>
 * <p>The detection rules are derived from the OWASP Core Rule Set (Apache-2.0). See
 * https://coreruleset.org/ .</p>
 * @topic 3. Security and Validation
 */
@MCElement(name = "sqlInjectionProtection")
public class SqlInjectionProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionProtectionInterceptor.class);

    public static final String X_PROTECTION = "X-Protection";

    public enum OnDetect {BLOCK, WARN}

    private int level = 1;
    private boolean inspectHeaders = false;
    private OnDetect onDetect = OnDetect.BLOCK;

    private SqlInjectionProtection protection;

    public SqlInjectionProtectionInterceptor() {
        name = "sql injection protection";
        setAppliedFlow(REQUEST_RESPONSE_FLOW);
    }

    @Override
    public void init() {
        super.init();
        if (level < 1 || level > 4)
            throw new ConfigurationException("sqlInjectionProtection: level must be between 1 and 4, was " + level);
        var ruleSet = SqlInjectionRuleSet.loadCrsRules(level);
        protection = new SqlInjectionProtection(ruleSet, inspectHeaders, router.getConfiguration().getUriFactory());
        log.debug("Loaded {} SQL injection rules (paranoia level <= {})", ruleSet.size(), level);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handle(exc, Flow.REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handle(exc, Flow.RESPONSE);
    }

    private Outcome handle(Exchange exc, Flow flow) {
        try {
            var hit = protection.scan(getMessage(exc, flow));
            if (hit.isEmpty())
                return CONTINUE;

            Detection d = hit.get();
            // Detecting a signature is normal gateway operation -> INFO, not WARN.
            if (onDetect == OnDetect.WARN) {
                log.info("SQL injection detected (rule {}) in {} {} but onDetect=warn, passing through: {}",
                        d.rule().id(), flow, d.location(), d.rule().message());
                return CONTINUE;
            }

            log.info("Blocked SQL injection (rule {}) in {} {}: {}",
                    d.rule().id(), flow, d.location(), d.rule().message());
            boolean request = flow == Flow.REQUEST;
            security(router.getConfiguration().isProduction(), getDisplayName())
                    .title((request ? "Request" : "Response") + " blocked by SQL injection protection")
                    .status(request ? 400 : 502)
                    .detail(request
                            ? "Request was rejected because it appears to contain a SQL injection attempt."
                            : "Upstream response was rejected because it appears to contain a SQL injection attempt.")
                    .buildAndSetResponse(exc);
            exc.getResponse().getHeader().add(X_PROTECTION, "Content violates SQL injection security policy");
            return ABORT;
        } catch (Exception e) {
            log.error("Error inspecting {} for SQL injection", flow, e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .status(500)
                    .detail("Error inspecting " + flow + "!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    /**
     * @description CRS paranoia level (1-4). Higher levels enable more rules: more aggressive detection at the
     * cost of more false positives. Level 1 is recommended for most APIs.
     * @default 1
     */
    @MCAttribute
    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * @description Whether headers are inspected. Applies to the message being scanned: request headers in the
     * request flow, response headers (e.g. from an untrusted upstream behind an outbound gateway) in the response
     * flow. Off by default because legitimate headers (User-Agent, Referer, Set-Cookie, ...) trigger false
     * positives more readily than parameters.
     * @default false
     */
    @MCAttribute
    public void setInspectHeaders(boolean inspectHeaders) {
        this.inspectHeaders = inspectHeaders;
    }

    public boolean isInspectHeaders() {
        return inspectHeaders;
    }

    /**
     * @description What to do on detection: <code>block</code> rejects a detected request with HTTP 400 and a
     * detected response with HTTP 502; <code>warn</code> only logs, allowing the message through (useful while tuning).
     * @default block
     */
    @MCAttribute
    public void setOnDetect(OnDetect onDetect) {
        this.onDetect = onDetect;
    }

    public OnDetect getOnDetect() {
        return onDetect;
    }

    @Override
    public String getShortDescription() {
        return "Protects against SQL injection attacks.";
    }
}
