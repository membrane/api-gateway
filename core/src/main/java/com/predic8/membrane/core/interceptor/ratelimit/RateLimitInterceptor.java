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

import java.io.UnsupportedEncodingException;

import com.predic8.membrane.core.util.*;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormat;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.ErrorUtil.createAndSetErrorResponse;
import static java.util.Locale.US;

/**
 * @description Allows rate limiting (Experimental)
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractInterceptor {

	public RateLimitStrategy rateLimitStrategy;

	protected static DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
	protected DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZoneUTC().withLocale(US);

	public RateLimitInterceptor() {
		this(Duration.standardHours(1), 1000);
	}

	public RateLimitInterceptor(Duration requestLimitDuration, int requestLimit) {
		rateLimitStrategy = new LazyRateLimit(requestLimitDuration, requestLimit);
		name = "RateLimiter";
		setFlow(REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String addr = exc.getRemoteAddrIp();
		if (rateLimitStrategy.isRequestLimitReached(addr)) {
			setResponseToServiceUnavailable(exc);
			return RETURN;
		}
		return CONTINUE;

	}

	public void setResponseToServiceUnavailable(Exchange exc) {
		createAndSetErrorResponse(exc,429, createErrorMessage(exc));
		exc.getResponse().setHeader(createHeaderFields(exc));
	}

	private Header createHeaderFields(Exchange exc) {
		Header hd = new Header();
		hd.add("Date", dateFormatter.print(DateTime.now()));
		hd.add("X-LimitDuration", PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()));
		hd.add("X-LimitRequests", Integer.toString(rateLimitStrategy.requestLimit));
		hd.add("X-LimitReset", Long.toString(rateLimitStrategy.getServiceAvailableAgainTime(exc.getRemoteAddrIp()).getMillis()));
		return hd;
	}

	private String createErrorMessage(Exchange exc) {
		return exc.getRemoteAddrIp() + " exceeded the rate limit of " + rateLimitStrategy.requestLimit +
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
	 *              time in seconds
	 * @default PT3600S
	 */
	@MCAttribute
	public void setRequestLimitDuration(String rld) {
		setRequestLimitDuration(Duration.parse(rld));
	}

	public void setRequestLimitDuration(Duration rld) {
		rateLimitStrategy.setRequestLimitDuration(rld);
	}

	@Override
	public String getShortDescription() {
		return "Limits incoming requests. It limits to " + rateLimitStrategy.getRequestLimit() + " requests every " + PeriodFormat.getDefault().print(rateLimitStrategy.getRequestLimitDuration().toPeriod()) + ".";
	}
}
