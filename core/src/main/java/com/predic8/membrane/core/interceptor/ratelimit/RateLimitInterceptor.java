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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.spel.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.*;
import org.springframework.expression.spel.standard.*;

import java.time.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.Util.*;
import static java.lang.String.*;

/**
 * @description <p>The <i>rateLimiter</i> plugin limits the number of requests of a client in a period of time.
 * As a default the client requests are grouped by client-Ip address and then counted. There are lots of
 * possibilities to group the requests using the keyExpression. The requests can even be counted from different
 * clients together.</p>
 * <p>When the gateway is located behind a loadbalancer then
 * the client-Ip address is not the one from the client but the address from the balancer. To get the real Ip-address loadbalancers,
 * <i>Web Application Firewalls</i> and reverse proxies set the ip from the original client into the <i>X-Forwarded-For</i> HTTP
 * header field. The limiter plugin can take the Ip-address from the header.</p>
 * <p>
 * The X-Forwarded-For header can only be trusted when a trustworthy reverse proxy or load balancer is between the client and server. The gateway not should be
 * reachable directly. Only activate this feature when you know what you are doing.
 * </p>
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For">X-Forwarded-For @Mozilla</a>
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractInterceptor {

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

    private final RateLimitStrategy strategy;

    private String keyExpression;
    private Expression expression;

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
        name = "RateLimiter";
        setFlow(REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        try {
            if (!strategy.isRequestLimitReached(getKey(exc)))
                return CONTINUE;
        } catch (SpelEvaluationException e) {
            log.error("Cannot evaluate keyExpression '{}' cause is {}",keyExpression,e.getMessage());
            exc.setResponse(createProblemDetails(500, "/internal-error", "Internal Server Error"));
            return RETURN;
        }

        Map<String,Object> details = new HashMap<>();
        details.put("message","The quota of the ratelimiter is exceeded. Try again in %s seconds.".formatted(strategy.getLimitReset(exc.getRemoteAddrIp())));
        exc.setResponse(createProblemDetails(429, "/ratelimiter/exceeded", "Rate limit is exceeded", details));
        setHeaderRateLimitFieldsOnResponse(exc);

        return RETURN;
    }

    @Override
    public void init() throws Exception {
        super.init();
        if (keyExpression == null || keyExpression.isBlank())
            return;
        expression = new SpelExpressionParser().parseExpression(keyExpression);
    }

    private String getKey(Exchange exc) {
        if (keyExpression == null) {
            return getClientIp(exc);
        }

        String result = expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()).getStandardEvaluationContext(), String.class);
        if (result != null)
            return result;

        log.warn("The expression {} evaluates to null or there is an error in the expression. This may result in a wrong counting for the ratelimiter.", expression);
        return "null";
    }

    protected String getClientIp(Exchange exc) {
        if (!trustForwardedFor || exc.getRequest().getHeader().getXForwardedFor() == null) {
            return useRemoteIpAddress(exc);
        }

        List<String> xForwardedFor = getForwardedForList(exc);
        if (xForwardedFor.isEmpty())
            return useRemoteIpAddress(exc);

        log.debug("X-Forwared-For {}",xForwardedFor);

        if (trustedProxyList != null && !trustedProxyList.isEmpty()) {
            log.debug("Checking list of trusted proxies");
            for (int i = 1; i <= trustedProxyList.size(); i++) {
                String trustedProxy = trustedProxyList.get(trustedProxyList.size() - i);
                String forwardedFor = xForwardedFor.get(xForwardedFor.size() - i);
                log.debug("Checking proxy {} against {}", trustedProxy, forwardedFor );
                if (!Objects.equals(trustedProxy, forwardedFor)) {
                    log.info("Trusted proxy {} is not in X-Forwarded-For list {}, or not on the right position.", trustedProxy, xForwardedFor);
                    return useRemoteIpAddress(exc);
                }
            }
            String clientIp = getOneBeforeTrustworthyProxy(xForwardedFor, trustedProxyList.size());
            log.debug("Using {} as client ip.",clientIp);
            return clientIp;
        }

        if (trustedProxyCount != -1) {
            log.debug("Using trustedProxyCount of {}", trustedProxyCount);
            if (xForwardedFor.size() <= trustedProxyCount) {
                log.info("Forwarded-For entries {} do not match trusted proxies {}",xForwardedFor,trustedProxyList);
                return useRemoteIpAddress(exc);
            }
            // e.g.:
            // 3 entries in X-Forwarded-For = a.b.c
            // trustedProxyCount = 2
            // 3 - 2 - 1 = 0 = First entry from the left
            // See tests
            String clientIp = getOneBeforeTrustworthyProxy(xForwardedFor, trustedProxyCount);
            log.debug("Client ip is {}" + clientIp);
            return clientIp;
        }

        if (trustedProxyList == null) {
            log.debug("No trustedProxyCount and no trustedProxyList.");
            if (xForwardedFor.size() != 1) {
                String ip = exc.getRemoteAddrIp();
                log.debug("More than 1 entry in X-Forwarded-For. Using ip address {}", ip);
                return ip;
            }
            String ff = xForwardedFor.get(0);
            log.debug("Using entry {} in X-Forwarded-For", ff);
            return ff;
        }

        return useRemoteIpAddress(exc);
    }

    private static String useRemoteIpAddress(Exchange exc) {
        String ip = exc.getRemoteAddrIp();
        log.debug("Using ip {}",ip);
        return ip;
    }

    /**
     * Take out the last entry, cause that was added by Membrane.
     *
     */
    private static List<String> getForwardedForList(Exchange exc) {
        List<String> xForwardedFor = splitStringByComma(exc.getRequest().getHeader().getNormalizedValue(X_FORWARDED_FOR));
        if (xForwardedFor.size() > 0)
            xForwardedFor.remove(xForwardedFor.size() -1  );
        return xForwardedFor;
    }

    protected static String getOneBeforeTrustworthyProxy(List<String> l, int count) {
        return l.get(l.size() - count - 1).trim();
    }

    private void setHeaderRateLimitFieldsOnResponse(Exchange exc) {
        Header h = exc.getResponse().getHeader();
        h.add(X_RATELIMIT_DURATION, strategy.getLimitDurationPeriod());
        h.add(X_RATELIMIT_LIMIT, Integer.toString(strategy.requestLimit));
        h.add(X_RATELIMIT_RESET, strategy.getLimitReset(exc.getRemoteAddrIp()));
    }

    @SuppressWarnings("unused")
    public int getRequestLimit() {
        return strategy.requestLimit;
    }

    /**
     * @description Number of requests within the period of measurement.
     * @default 1000
     */
    @MCAttribute
    public void setRequestLimit(int limit) {
        strategy.setRequestLimit(limit);
    }

    public String getRequestLimitDuration() {
        return strategy.requestLimitDuration.toString();
    }

    /**
     * @description Duration after the limit is reset in the <i>ISO 8600 Duration</i> format, e.g. PT10S for 10 seconds,
     * PT5M for 5 minutes or PT8H for eight hours.
     * @see <a href="https://en.wikipedia.org/wiki/ISO_8601#Durations">ISO 8601 Durations</a>
     * @default PT3600S
     */
    @MCAttribute
    public void setRequestLimitDuration(String duration) {
        setRequestLimitDuration(Duration.parse(duration));
    }

    public void setRequestLimitDuration(Duration duration) {
        strategy.setRequestLimitDuration(duration);
    }

    /**
     * @description The expression the ratelimiter should use to group the requests before counting. The Spring Expression Language (SpEL)
     * is used as language. In the expression the build-in variables request, header, properties can be used.
     * @default ip-address
     */
    @MCAttribute
    public void setKeyExpression(String expression) {
        this.keyExpression = expression;
    }

    @SuppressWarnings("unused")
    public String getKeyExpression() {
        return keyExpression;
    }

    public String getTrustedProxyList() {
        return trustedProxyList == null ? null : join(",", trustedProxyList);
    }

    /**
     * @description Comma separated list of trusted proxy servers and loadbalancers. Used to evaluate the X-Forwarded-For header.
     * If both <b>trustedProxyList</b> and <b>trustedProxyCount</b> is specified, the trustedProxyList is used to
     * determine the client ip address. To make this configuration active set <pre>isTrustForwardedFor</pre> to true.
     * @default empty String
     */
    @MCAttribute
    public void setTrustedProxyList(String trustedProxyList) {
        this.trustedProxyList = Arrays.stream(trustedProxyList.split(",")).map(String::trim).toList();
    }

    @SuppressWarnings("unused")
    public int getTrustedProxyCount() {
        return trustedProxyCount;
    }

    /**
     * @description Number of trusted proxy servers and loadbalancers. Used to evaluate the X-Forwarded-For header.
     * If both <pre>trustedProxyList</pre> and <pre>trustedProxyCount</pre> is specified, the trustedProxyList is used to
     * determine the client ip address. To make this configuration active set <pre>isTrustForwardedFor</pre> to true.
     * @default 0
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
     * @description Set this only to true if you know that are you doing. The function of the ratelimter relys on corrent X-ForwaredFor header values.
     * @default false
     */
    @MCAttribute
    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public String getShortDescription() {
        return "Limits incoming requests. It limits to " + strategy.getRequestLimit() + " requests every " + strategy.getRequestLimitDuration().toString() + ".";
    }
}