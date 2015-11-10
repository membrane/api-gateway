package com.predic8.membrane.core.interceptor.ratelimit;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.apache.http.entity.ContentType;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.PeriodFormat;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

/**
 * @description Allows rate limiting
 */
@MCElement(name = "rateLimiter")
public class RateLimitInterceptor extends AbstractInterceptor {

	enum RateLimitStrategyType {
		LAZY, PRECISE
	}

	public RateLimitStrategy rateLimitStrategy;
	private RateLimitStrategyType mode;
	
	public RateLimitInterceptor()
	{
		this(RateLimitStrategyType.LAZY,Duration.standardHours(1),1000);
	}

	public RateLimitInterceptor(RateLimitStrategyType type, Duration requestLimitDuration, int requestLimit) {
		switch (type) {
		case LAZY:
			rateLimitStrategy = new LazyRateLimit(requestLimitDuration, requestLimit);
			break;
		case PRECISE:
			rateLimitStrategy = new PreciseRateLimit(requestLimitDuration, requestLimit);
			break;
		default:
			rateLimitStrategy = new LazyRateLimit(requestLimitDuration, requestLimit);
		}
		mode = type;
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

		Header hd = exc.getResponse().getHeader();
		hd.add("Status", "429 Too Many Requests");
		DateTimeFormatter dateFormatter = 
			    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
			    .withZoneUTC().withLocale(Locale.US);
		hd.add("Date", dateFormatter.print(DateTime.now()));
		hd.add("X-LimitDuration", PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()));
		hd.add("X-LimitRequests", Integer.toString(rateLimitStrategy.requestLimit));
		String ip = exc.getRemoteAddrIp();
		DateTime availableAgainDateTime = rateLimitStrategy.getServiceAvailableAgainTime(ip);
		hd.add("X-LimitReset", Long.toString(availableAgainDateTime.getMillis()));

		StringBuilder bodyString = new StringBuilder();
		bodyString.setLength(0);
		bodyString.append(ip);
		bodyString.append(" exceeded the rate limit of ");
		bodyString.append(rateLimitStrategy.requestLimit);
		bodyString.append(" requests per ");
		bodyString.append(PeriodFormat.getDefault().print(rateLimitStrategy.requestLimitDuration.toPeriod()));
		bodyString.append(". The next request can be made at ");
		DateTimeFormatter dtFormatter = DateTimeFormat.forPattern("HH:mm:ss aa");
		bodyString.append(dtFormatter.print(availableAgainDateTime));

		byte[] body = bodyString.toString().getBytes(exc.getResponse().getCharset());

		Response resp = ResponseBuilder.newInstance().status(429, "Too Many Requests.")
				.contentType(ContentType.TEXT_PLAIN.toString()).header(hd).body(body).build();
		exc.setResponse(resp);
	}

	public int getRequestLimit() {
		return rateLimitStrategy.requestLimit;
	}

	/**
	 * @description number of requests
	 * @Required
	 */
	@MCAttribute
	public void setRequestLimit(int rl) {
		rateLimitStrategy.requestLimit = rl;
	}

	public Duration getRequestLimitDuration() {
		return rateLimitStrategy.requestLimitDuration;
	}

	/**
	 * @description Duration after the limit is reset
	 * @Required
	 */
	@MCAttribute
	public void setRequestLimitDuration(Duration rld) {
		// Wahrscheinlich können aus der config nur bestimmte datentypen
		// ausgelesen werden. Hier wird dann eher ein String gelesen und aus
		// diesem das Datum generiert.
	}

	public RateLimitStrategyType getMode() {
		return mode;
	}

	/**
	 * @description Precise or Lazy mode
	 * @Required
	 */
	@MCAttribute
	public void setMode(RateLimitStrategyType mode) {
		this.mode = mode;
	}

}
