/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.ratelimit;

import com.google.common.net.InetAddresses;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.Header.RETRY_AFTER;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.ratelimit.RateLimitStrategy.secondsUntil;
import static com.predic8.membrane.core.lang.ExchangeExpression.expression;
import static com.predic8.membrane.core.util.HttpUtil.getForwardedForList;
import static java.lang.String.join;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;

/**
 * @description <p>Limits how many requests a client may send in a period of time. Further requests are rejected with
 * 429 Too Many Requests until the period has elapsed. Requests are counted per client IP address, or by a value
 * computed from the request, e.g. an API key, a user or a field of the body. The value can be computed with SpEL,
 * JSONPath, XPath or Groovy.</p>
 * <p>Responses carry the quota in the headers <code>X-RateLimit-Limit</code>, <code>X-RateLimit-Remaining</code>,
 * <code>X-RateLimit-Reset</code> (seconds until the counts are reset) and <code>X-RateLimit-Duration</code>. A rejected
 * request also gets <code>Retry-After</code>. When several limiters apply to a request, the headers describe the one
 * with the fewest requests left.</p>
 * <p>The counts of all clients are reset together. They are kept in memory, so every Membrane instance counts on its
 * own.</p>
 * <p>Behind a load balancer or reverse proxy, the client address can be taken from the <code>X-Forwarded-For</code>
 * header. Trust it only if Membrane cannot be reached directly; otherwise a client can choose its own address.</p>
 * <p>See tutorials/rate-limiting/10-Rate-Limit-by-Client-IP.yaml.</p>
 * @topic 3. Security and Validation
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - rateLimiter:
 *         requestLimit: 10
 *         requestLimitDuration: PT1M
 * </code></pre>
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class.getName());

    /**
     * The names are chosen based on
     * - <a href="https://www.ietf.org/archive/id/draft-polli-ratelimit-headers-02.html">RateLimit Header Fields for HTTP</a>, still a draft
     */
    public static final String X_RATELIMIT_DURATION = "X-RateLimit-Duration";
    public static final String X_RATELIMIT_LIMIT = "X-RateLimit-Limit";

    /**
     * Number of seconds until the quota resets.
     */
    public static final String X_RATELIMIT_RESET = "X-RateLimit-Reset";

    /**
     * Number of requests left in the current period.
     */
    public static final String X_RATELIMIT_REMAINING = "X-RateLimit-Remaining";

    /**
     * Holds the {@link Quota} that is reported in the response headers.
     */
    private static final String QUOTA_PROPERTY = "membrane.ratelimit.quota";

    private static final String KEY_PREFIX_EXPRESSION = "key:";
    private static final String KEY_PREFIX_CLIENT_IP = "ip:";

    private final RateLimitStrategy strategy;

    private List<String> trustedProxyList;

    /**
     * -1 means no proxy is trusted
     */
    private int trustedProxyCount = -1;

    private boolean trustForwardedFor;

    public RateLimitInterceptor() {
        // Needed even if there are no usages
        this(Duration.ofHours(1), 1000);
    }

    public RateLimitInterceptor(Duration requestLimitDuration, int requestLimit) {
        strategy = new LazyRateLimit(requestLimitDuration, requestLimit);
        name = "rate limiter";
        setAppliedFlow(REQUEST_RESPONSE_FLOW);
    }

    @Override
    protected ExchangeExpression getExchangeExpression() {
        // If there is no expression use the client IP
        if (expression.isEmpty())
            return null;
        return expression(this, language, expression);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        final String key;
        try {
            key = getKey(exc);
        } catch (ExchangeExpressionException e) {
            // Only body errors get here, getKey() falls back to the client IP for any other error.
            // The limiter only runs in the request flow, so the body is the client's (400)
            e.problemDetails(router.getConfiguration().isProduction(), getDisplayName(), REQUEST)
                    .addSubType("rate-limit")
                    .title("Cannot evaluate keyExpression.")
                    .buildAndSetResponse(exc);
            return RETURN;
        }
        final var requestCount = strategy.countRequest(key);
        final var quota = new Quota(getRequestLimit(), Math.max(0, getRequestLimit() - requestCount.count()), requestCount.resetAt(), strategy.getLimitDurationPeriod());
        if (requestCount.count() <= getRequestLimit()) {
            reportIfMostRestrictive(exc, quota);
            return CONTINUE;
        }

        // The limiter that rejects the request reports its own quota, even if another one has as few requests left
        exc.setProperty(QUOTA_PROPERTY, quota);
        log.info("{} limit: {} duration: {} is exceeded. (clientIp: {})", key, getRequestLimit(), getRequestLimitDuration(), exc.getRemoteAddrIp());
        final var reset = secondsUntil(quota.resetAt());
        user(router.getConfiguration().isProduction(), getDisplayName())
                .status(429)
                .title("Rate limit is exceeded.")
                .addSubType("rate-limit")
                .detail("The quota of the rate limit is exceeded. Try again in %s seconds.".formatted(reset), true)
                .topLevel("limit", getRequestLimit())
                .topLevel("duration", getRequestLimitDuration())
                .buildAndSetResponse(exc);
        setQuotaHeaders(exc, quota, reset);
        exc.getResponse().getHeader().setValue(RETRY_AFTER, reset);

        return RETURN;
    }

    /**
     * Adds the quota headers to responses that passed the limiter. Called for the rejection of
     * a later limiter as well, which then reports its own quota.
     */
    @Override
    public Outcome handleResponse(Exchange exc) {
        // The reset time is computed now, as the backend may have taken a while
        if (exc.getProperty(QUOTA_PROPERTY) instanceof Quota quota)
            setQuotaHeaders(exc, quota, secondsUntil(quota.resetAt()));
        return CONTINUE;
    }

    /**
     * With several limiters on the way of a request, e.g. a global one and one in the API,
     * the client is told about the one with the fewest requests left.
     */
    private static void reportIfMostRestrictive(Exchange exc, Quota quota) {
        if (exc.getProperty(QUOTA_PROPERTY) instanceof Quota reported && reported.remaining() <= quota.remaining())
            return;
        exc.setProperty(QUOTA_PROPERTY, quota);
    }

    private static void setQuotaHeaders(Exchange exc, Quota quota, String reset) {
        final var h = exc.getResponse().getHeader();
        h.setValue(X_RATELIMIT_LIMIT, Integer.toString(quota.limit()));
        h.setValue(X_RATELIMIT_REMAINING, Integer.toString(quota.remaining()));
        h.setValue(X_RATELIMIT_RESET, reset);
        h.setValue(X_RATELIMIT_DURATION, quota.duration());
    }

    /**
     * @param resetAt when the counts are reset. The seconds until then are computed when the headers are written.
     */
    private record Quota(int limit, int remaining, LocalDateTime resetAt, String duration) {
    }

    /**
     * With a keyExpression, expression values and the client IP fallback get different prefixes,
     * so a client cannot choose a key value that shares the fallback bucket of another client.
     */
    private String getKey(Exchange exc) {
        if (expression == null || expression.isEmpty()) {
            return getClientIp(exc);
        }

        final String value;
        try {
            value = exchangeExpression.evaluate(exc, REQUEST, String.class);
        } catch (Exception e) {
            // A broken body is the client's error and is answered with 400 instead of being counted.
            // An unreadable body (e.g. invalid Content-Encoding) is answered by FlowController
            var bodyFailure = throwableOfType(e, ReadingBodyException.class);
            if (bodyFailure != null)
                throw bodyFailure;
            if (e instanceof ExchangeExpressionException eee && eee.isBodyError())
                throw eee;
            log.info("Error evaluating expression {} for rate limit. Fallback to client IP.", expression); // Can be pretty common
            return KEY_PREFIX_CLIENT_IP + getClientIp(exc);
        }
        if (value != null && !value.isEmpty())
            return KEY_PREFIX_EXPRESSION + value;

        log.info("The expression {} evaluates to null or an empty value. Fallback to client IP.", expression);
        return KEY_PREFIX_CLIENT_IP + getClientIp(exc);
    }

    protected String getClientIp(Exchange exc) {
        String supposedClientId = computeClientIpFromForwards(exc);
        log.debug("Using client ip {}", supposedClientId);
        return supposedClientId;
    }

    private String computeClientIpFromForwards(Exchange exc) {
        if (!trustForwardedFor || exc.getRequest().getHeader().getXForwardedFor() == null) {
            return useRemoteIpAddress(exc);
        }

        List<String> xForwardedFor = getForwardedForList(exc);
        if (xForwardedFor.isEmpty())
            return useRemoteIpAddress(exc);

        log.debug("X-Forwared-For {}", xForwardedFor);

        if (trustedProxyList != null && !trustedProxyList.isEmpty()) {
            return getClientIPfromTrustedProxyList(exc, xForwardedFor);
        }

        if (trustedProxyCount != -1) {
            return getClientIPFromTrustedProxyCount(exc, xForwardedFor);
        }

        log.debug("No trustedProxyCount and no trustedProxyList.");
        if (xForwardedFor.size() != 1) {
            log.debug("More than 1 entry in X-Forwarded-For.");
            return exc.getRemoteAddrIp();
        }
        log.debug("Using entry in X-Forwarded-For");
        return xForwardedFor.getFirst();
    }

    private String getClientIPFromTrustedProxyCount(Exchange exc, List<String> xForwardedFor) {
        log.debug("Using trustedProxyCount of {}", trustedProxyCount);
        if (xForwardedFor.size() <= trustedProxyCount) {
            log.info("Forwarded-For entries {} do not match trusted proxy count {}", xForwardedFor, trustedProxyCount);
            return useRemoteIpAddress(exc);
        }
        // e.g.:
        // 3 entries in X-Forwarded-For = a.b.c
        // trustedProxyCount = 2
        // 3 - 2 - 1 = 0 = First entry from the left
        // See tests
        return getOneBeforeTrustworthyProxy(xForwardedFor, trustedProxyCount);
    }

    /**
     * Walks from the connection peer towards the client: as long as the address is a trusted proxy, the X-Forwarded-For
     * entry it added is followed. The first address that is not a trusted proxy is the client. Entries left of it were
     * written by the client and are ignored.
     */
    private String getClientIPfromTrustedProxyList(Exchange exc, List<String> xForwardedFor) {
        final var peer = exc.getRemoteAddrIp();
        if (peer == null || !isTrustedProxy(peer)) {
            log.debug("Peer {} is not a trusted proxy, ignoring X-Forwarded-For.", peer);
            return peer;
        }
        for (final var address : xForwardedFor.reversed()) {
            if (!isTrustedProxy(address))
                return address;
        }
        log.debug("All addresses in X-Forwarded-For {} are trusted proxies, using the peer address.", xForwardedFor);
        return peer;
    }

    /**
     * Compares IP addresses by value, so that e.g. <code>::1</code> in the list matches the peer address
     * <code>0:0:0:0:0:0:0:1</code>.
     */
    private boolean isTrustedProxy(String address) {
        return trustedProxyList.contains(normalizeAddress(address));
    }

    /**
     * @return an IP address literal in its canonical form, anything else as it is, e.g. an IPv6 address with a scope
     * that does not exist on this host. Never resolves a host name.
     */
    static String normalizeAddress(String address) {
        final var trimmed = address.trim();
        if (!InetAddresses.isInetAddress(trimmed))
            return trimmed;
        try {
            return InetAddresses.toAddrString(InetAddresses.forString(trimmed));
        } catch (IllegalArgumentException e) {
            return trimmed;
        }
    }

    private static String useRemoteIpAddress(Exchange exc) {
        return exc.getRemoteAddrIp();
    }

    protected static String getOneBeforeTrustworthyProxy(List<String> l, int count) {
        return l.get(l.size() - count - 1).trim();
    }

    @SuppressWarnings("unused")
    public int getRequestLimit() {
        return strategy.requestLimit;
    }

    /**
     * @description Number of requests a client may send per period.
     * @default 1000
     * @example 100
     */
    @MCAttribute
    public void setRequestLimit(int limit) {
        strategy.setRequestLimit(limit);
    }

    public String getRequestLimitDuration() {
        return strategy.requestLimitDuration.toString();
    }

    /**
     * @description Length of the period as an ISO 8601 duration, e.g. <code>PT10S</code> for 10 seconds,
     * <code>PT5M</code> for 5 minutes or <code>PT8H</code> for 8 hours.
     * @default PT1H
     * @example PT1M
     */
    @MCAttribute
    public void setRequestLimitDuration(String duration) {
        setRequestLimitDuration(Duration.parse(duration));
    }

    public void setRequestLimitDuration(Duration duration) {
        strategy.setRequestLimitDuration(duration);
    }

    /**
     * @description Expression whose value the requests are counted by, e.g. <code>headers['X-Api-Key']</code> in SpEL
     * or <code>$.user</code> in JSONPath. If the expression fails or yields no value, the request is counted by its
     * client IP address. A body the expression cannot parse as JSON or XML is rejected with 400.
     * @default client IP address
     * @example headers['X-Api-Key']
     */
    @MCAttribute
    public void setKeyExpression(String expression) {
        this.expression = expression;
    }

    @SuppressWarnings("unused")
    public String getKeyExpression() {
        return expression;
    }

    public String getTrustedProxyList() {
        return trustedProxyList == null ? null : join(",", trustedProxyList);
    }

    /**
     * @description Comma separated list of the IP addresses of trusted proxy servers and loadbalancers, including the
     * one that connects to Membrane. Used to evaluate the X-Forwarded-For header: starting with the address of the
     * connection, trusted addresses are skipped from the end of the header, and the first untrusted address is the
     * client. If the connection does not come from a trusted address, its address is used and the header is ignored.
     * Each trusted proxy must append the address it received the request from to X-Forwarded-For (or replace the
     * header with it); a proxy that only passes on the client's header lets the client choose its address.
     * If both <b>trustedProxyList</b> and <b>trustedProxyCount</b> is specified, the trustedProxyList is used to
     * determine the client ip address. To make this configuration active set <pre>isTrustForwardedFor</pre> to true.
     * @default empty String
     */
    @MCAttribute
    public void setTrustedProxyList(String trustedProxyList) {
        this.trustedProxyList = Arrays.stream(trustedProxyList.split(",")).map(RateLimitInterceptor::normalizeAddress).toList();
    }

    @SuppressWarnings("unused")
    public int getTrustedProxyCount() {
        return trustedProxyCount;
    }

    /**
     * @description Number of entries at the end of <code>X-Forwarded-For</code> that were added by trusted proxies.
     * The entry to the left of them is the client address. If there is none, the address of the connection is used.
     * Behind a single load balancer that appends the client address, this is 0. Applies only when
     * <code>trustForwardedFor</code> is true; <code>trustedProxyList</code> takes precedence.
     * @default not set
     * @example 0
     */
    @MCAttribute
    public void setTrustedProxyCount(int trustedProxyCount) {
        this.trustedProxyCount = trustedProxyCount;
    }

    @SuppressWarnings("unused")
    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    /**
     * @description Takes the client address from the <code>X-Forwarded-For</code> header instead of the connection.
     * Enable it only if all requests come through trusted proxies, because a client can set the header itself.
     * Without <code>trustedProxyList</code> or <code>trustedProxyCount</code>, the header is used only if it has
     * exactly one entry.
     * @default false
     */
    @MCAttribute
    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public String getShortDescription() {
        return "Limits incoming requests to %s requests every %s.".formatted(strategy.getRequestLimit(), strategy.getRequestLimitDuration());
    }
}