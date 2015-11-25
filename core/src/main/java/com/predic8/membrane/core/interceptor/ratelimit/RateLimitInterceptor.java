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
import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * @description Allows rate limiting (Experimental)
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractInterceptor {

	public RateLimitStrategy rateLimitStrategy;

	public RateLimitInterceptor() {
		this(Duration.standardHours(1), 1000);
	}

	public RateLimitInterceptor(Duration requestLimitDuration, int requestLimit) {
		rateLimitStrategy = new LazyRateLimit(requestLimitDuration, requestLimit);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String addr = exc.getRemoteAddrIp();
		if (rateLimitStrategy.isRequestLimitReached(addr)) {
			setResponseToServiceUnavailable(exc);
			return Outcome.RETURN;
		}
		return Outcome.CONTINUE;

	}

	public void setResponseToServiceUnavailable(Exchange exc) throws UnsupportedEncodingException {

		Header hd = new Header();
		DateTimeFormatter dateFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZoneUTC()
				.withLocale(Locale.US);
		hd.add("Date", dateFormatter.print(DateTime.now()));
		hd.add("X-LimitDuration", PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()));
		hd.add("X-LimitRequests", Integer.toString(rateLimitStrategy.requestLimit));
		String ip = exc.getRemoteAddrIp();
		DateTime availableAgainDateTime = rateLimitStrategy.getServiceAvailableAgainTime(ip);
		hd.add("X-LimitReset", Long.toString(availableAgainDateTime.getMillis()));

		StringBuilder bodyString = new StringBuilder();
		DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
		bodyString.append(ip).append(" exceeded the rate limit of ").append(rateLimitStrategy.requestLimit)
				.append(" requests per ")
				.append(PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()))
				.append(". The next request can be made at ").append(dtFormatter.print(availableAgainDateTime));

		Response resp = ResponseBuilder.newInstance().status(429, "Too Many Requests.")
				.contentType(MimeType.TEXT_PLAIN_UTF8).header(hd).body(bodyString.toString()).build();
		exc.setResponse(resp);
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

}
