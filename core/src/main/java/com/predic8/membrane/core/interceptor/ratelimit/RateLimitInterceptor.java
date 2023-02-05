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
import org.joda.time.*;
import org.joda.time.format.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.ErrorUtil.*;
import static java.util.Locale.*;

/**
 * @description Rate limiting
 *
 * @TODO add ipaddress to Evaluation Context
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class.getName());

    public RateLimitStrategy rateLimitStrategy;

    protected static DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
    protected DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZoneUTC().withLocale(US);
    private String keyExpression;
    private Expression expression;

    public RateLimitInterceptor() {
        // Needed even if there are no usages
        this(Duration.standardHours(1), 1000);
    }

    public RateLimitInterceptor(Duration requestLimitDuration, int requestLimit) {
        rateLimitStrategy = new LazyRateLimit(requestLimitDuration, requestLimit);
        name = "RateLimiter";
        setFlow(REQUEST);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (rateLimitStrategy.isRequestLimitReached(getKey(exc))) {
            setResponseToServiceUnavailable(exc);
            exc.getResponse().getHeader().setContentType(APPLICATION_PROBLEM_JSON);
            exc.getResponse().setBodyContent(createProblemDetails("http://membrane-api.io/ratelimiter/exceeded", "Rate Limit is Exceeded", null));
            return RETURN;
        }
        return CONTINUE;

    }

    @Override
    public void init() throws Exception {
        super.init();
        expression = new SpelExpressionParser().parseExpression(keyExpression);
    }

    private String getKey(Exchange exc) {
        if (keyExpression == null)
            return exc.getRemoteAddrIp();

        String result = expression.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()).getStandardEvaluationContext(), String.class);
        if (result != null)
            return result;

        log.warn("The expression {} evaluates to null or there is an error in the expression. This may result in a wrong counting for the ratelimiter.", expression);
        return "null";
    }

    public void setResponseToServiceUnavailable(Exchange exc) {
        createAndSetErrorResponse(exc, 429, createErrorMessage(exc));
        exc.getResponse().setHeader(createHeaderFields(exc));
    }

    private Header createHeaderFields(Exchange exc) {
        Header hd = new Header();
        hd.add("Date", dateFormatter.print(DateTime.now()));
        hd.add("X-LimitDuration", rateLimitStrategy.getLimitDurationPeriod());
        hd.add("X-LimitRequests", Integer.toString(rateLimitStrategy.requestLimit));
        hd.add("X-LimitReset", rateLimitStrategy.getLimitReset(exc.getRemoteAddrIp()));
        return hd;
    }

    private String createErrorMessage(Exchange exc) {
        return getKey(exc) + " exceeded the rate limit of " + rateLimitStrategy.requestLimit +
               " requests per " +
               PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()) +
               ". The next request can be made at " + dtFormatter.print(rateLimitStrategy.getServiceAvailableAgainTime(exc.getRemoteAddrIp()));
    }

    public int getRequestLimit() {
        return rateLimitStrategy.requestLimit;
    }

    /**
     * @description number of requests
     * @default 1000
     */
    @MCAttribute
    public void setRequestLimit(int rl) {
        rateLimitStrategy.setRequestLimit(rl);
    }

    public String getRequestLimitDuration() {
        return rateLimitStrategy.requestLimitDuration.toString();
    }

    /**
     * @description Duration after the limit is reset in PTxS where x is the
     * time in seconds
     * @default PT3600S
     */
    @MCAttribute
    public void setRequestLimitDuration(String rld) {
        setRequestLimitDuration(Duration.parse(rld));
    }

    public void setRequestLimitDuration(Duration rld) {
        rateLimitStrategy.setRequestLimitDuration(rld);
    }

    /**
     * @description The ratelimiter counts requests for a key.
     * @default ip-address
     */
    @MCAttribute
    public void setKeyExpression(String expression) {
        this.keyExpression = expression;
    }

    public String getKeyExpression() {
        return keyExpression;
    }

    @Override
    public String getShortDescription() {
        return "Limits incoming requests. It limits to " + rateLimitStrategy.getRequestLimit() + " requests every " + PeriodFormat.getDefault().print(rateLimitStrategy.getRequestLimitDuration().toPeriod()) + ".";
    }
}