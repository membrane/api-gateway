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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Response.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.lang.Thread.*;
import static org.joda.time.Duration.*;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimitInterceptorTest {

	private final static ObjectMapper om = new ObjectMapper();

	@ParameterizedTest
	@ValueSource( strings = {"properties[a]","path","json[foo]","method","path + method","headers.host","exchange.remoteAddrIp"})
	void simplePropertyExpression(String expression) throws Exception {

		Exchange exc1 = prepareRequest("aaa");
		Exchange exc2 = prepareRequest("bbb");

		RateLimitInterceptor interceptor = new RateLimitInterceptor(standardSeconds(10),3);
		interceptor.setKeyExpression(expression);
		interceptor.init();

		assertEquals(CONTINUE, interceptor.handleRequest(exc1));
		assertEquals(CONTINUE, interceptor.handleRequest(exc2));
		assertEquals(CONTINUE, interceptor.handleRequest(exc1));
		assertEquals(CONTINUE, interceptor.handleRequest(exc2));
		assertEquals(CONTINUE, interceptor.handleRequest(exc1));
		assertEquals(CONTINUE, interceptor.handleRequest(exc2));
		assertEquals(RETURN, interceptor.handleRequest(exc1));
		assertEquals(RETURN, interceptor.handleRequest(exc2));
	}

	@NotNull
	private static Exchange prepareRequest(String value) throws URISyntaxException, JsonProcessingException {
		Exchange exc = new Request.Builder()
				.method(value)
				.url(new URIFactory(),"/" + value)
				.header("Host",value)
				.buildExchange();
		exc.setProperty("a",value);
		exc.setRemoteAddrIp(value);

		Map<String,String> m = new HashMap<>();
		m.put("foo",value);
		exc.getRequest().setBodyContent(om.writeValueAsBytes(m));
		return exc;
	}

	@Test
	public void testHandleRequestRateLimit1Second() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().header("accept","html").build());
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(standardSeconds(rateLimitSeconds), tryLimit);

		for (int i = 0; i < tryLimit; i++) {
			assertEquals(CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(RETURN, rli.handleRequest(exc));

		sleep(1000);
		for (int i = 0; i < tryLimit; i++) {
			assertEquals(CONTINUE, rli.handleRequest(exc));
		}

		assertEquals(RETURN, rli.handleRequest(exc));

	}
	
	@Test
	public void testHandleRequestRateLimit1SecondConcurrency() throws Exception
	{
		int tryLimit = 16;
		int rateLimitSeconds = 1;
		final RateLimitInterceptor rli = new RateLimitInterceptor(standardSeconds(rateLimitSeconds), tryLimit);
		
		ArrayList<Thread> threads = new ArrayList<>();
		final AtomicInteger continues = new AtomicInteger();
		final AtomicInteger returns = new AtomicInteger();
		for(int i = 0; i < 1000; i++)
		{
			Thread t = new Thread(() -> {
				try {
					Outcome out = rli.handleRequest(getExchange());
					if(out == CONTINUE)
					{
						continues.incrementAndGet();
					}
					else if(out == RETURN)
					{
						returns.incrementAndGet();
					}
				} catch (Exception e) {
					e.printStackTrace();
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

	@NotNull
	private static Exchange getExchange() {
		final Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().header("accept","*/*").build());
		exc.setResponse(ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");
		return exc;
	}
}
