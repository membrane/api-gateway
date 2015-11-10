package com.predic8.membrane.core.interceptor.ratelimit;

import static org.junit.Assert.*;

import org.joda.time.Duration;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptor.RateLimitStrategyType;

public class RateLimitInterceptorTest {


	@Test
	public void testHandleRequestPreciseRateLimit1Second() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(RateLimitStrategyType.PRECISE,
				Duration.standardSeconds(rateLimitSeconds), tryLimit);

		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));

		Thread.sleep(1000);
		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));

	}

	@Test
	public void testHandleRequestLazyRateLimit1Second() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(RateLimitStrategyType.LAZY,
				Duration.standardSeconds(rateLimitSeconds), tryLimit);

		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));

		Thread.sleep(1000);
		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));

	}
	
	
	
	
	/*
	@Test
	public void testHandleRequestPreciseRateLimit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitMinutes = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(RateLimitStrategyType.PRECISE,
				Duration.standardMinutes(rateLimitMinutes), tryLimit);

		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));
	}

	@Test
	public void testHandleRequestLazyRateLimit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitMinutes = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(RateLimitStrategyType.LAZY,
				Duration.standardMinutes(rateLimitMinutes), tryLimit);

		for (int i = 0; i < tryLimit; i++) {
			assertEquals(Outcome.CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(Outcome.RETURN, rli.handleRequest(exc));

	}
*/

}
