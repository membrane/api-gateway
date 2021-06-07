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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.Duration;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.Outcome;

public class RateLimitInterceptorTest {

	@Test
	public void testHandleRequestRateLimit1Second() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(Duration.standardSeconds(rateLimitSeconds), tryLimit);

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
	public void testHandleRequestRateLimit1SecondConcurrency() throws Exception
	{
		final Exchange exc = new Exchange(null);
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		final RateLimitInterceptor rli = new RateLimitInterceptor(Duration.standardSeconds(rateLimitSeconds), tryLimit);
		
		ArrayList<Thread> threads = new ArrayList<>();
		final AtomicInteger continues = new AtomicInteger();
		final AtomicInteger returns = new AtomicInteger();
		for(int i = 0; i < 1000; i++)
		{
			Thread t = new Thread(() -> {
				try {
					Outcome out = rli.handleRequest(exc);
					if(out == Outcome.CONTINUE)
					{
						continues.incrementAndGet();
					}
					else if(out == Outcome.RETURN)
					{
						returns.incrementAndGet();
					}
				} catch (Exception ignored) {
				}
			});
			threads.add(t);
			t.start();
		}
		for(Thread t : threads)
		{
			t.join();
		}
		assertEquals(16, continues.get());
		assertEquals(984, returns.get());
	}
}
