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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.jose4j.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.slf4j.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.ratelimit.RateLimitInterceptor.*;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static java.lang.Long.*;
import static java.lang.Thread.*;
import static java.time.Duration.*;
import static java.util.stream.IntStream.*;
import static org.junit.jupiter.api.Assertions.*;

public class RateLimitInterceptorTest {

	private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptorTest.class.getName());

	private final static ObjectMapper om = new ObjectMapper();

	@Test
	void setLimitDuration() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor() {{
			setRequestLimitDuration("PT10S");
		}};
		assertEquals("PT10S",interceptor.getRequestLimitDuration());

		interceptor.setRequestLimitDuration("PT10M");
		assertEquals("PT10M",interceptor.getRequestLimitDuration());
	}

	@ParameterizedTest
	@ValueSource( strings = {"properties.a","path","json.foo","method","path + method","headers.host","exchange.remoteAddrIp"})
	void simplePropertyExpression(String expression) throws Exception {

		log.info("expression: {}", expression);

		Exchange exc1 = prepareRequest("aaa");
		Exchange exc2 = prepareRequest("bbb");

		RateLimitInterceptor interceptor = new RateLimitInterceptor(ofSeconds(10),3);
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


		Header h1 = exc1.getResponse().getHeader();
		assertEquals("PT10S", h1.getFirstValue(X_RATELIMIT_DURATION));
		assertEquals("3", h1.getFirstValue(X_RATELIMIT_LIMIT));

		assertTrue(parseLong(h1.getFirstValue(X_RATELIMIT_RESET)) > 0);
	}

	@Test
	void jsonpathExpression() throws URISyntaxException {
		Exchange excA = createJsonExchange("a");
		Exchange excB = createJsonExchange("b");

		RateLimitInterceptor interceptor = new RateLimitInterceptor(ofSeconds(10),3);
		interceptor.setLanguage(JSONPATH);
		interceptor.setKeyExpression("$.application");
		interceptor.init();

		assertEquals(CONTINUE, interceptor.handleRequest(excA));
		assertEquals(CONTINUE, interceptor.handleRequest(excB));
		assertEquals(CONTINUE, interceptor.handleRequest(excA));
		assertEquals(CONTINUE, interceptor.handleRequest(excB));
		assertEquals(CONTINUE, interceptor.handleRequest(excA));
		assertEquals(CONTINUE, interceptor.handleRequest(excB));
		assertEquals(RETURN, interceptor.handleRequest(excA));
		assertEquals(RETURN, interceptor.handleRequest(excB));

		assertEquals(429, excA.getResponse().getStatusCode());
		assertEquals(429, excB.getResponse().getStatusCode());
	}

	private static Exchange createJsonExchange(String application) throws URISyntaxException {
        return post("/foo")
                .json("""
                        {
                             "application": "%s"
                        }
                        """.formatted(application))
                .buildExchange();
	}

	@NotNull
	private static Exchange prepareRequest(String value) throws URISyntaxException, JsonProcessingException {
		Exchange exc = new Request.Builder()
				.method(value)
				.url(new URIFactory(),"/" + value)
				.header("Host",value)
				.body(om.writeValueAsBytes(Map.of("foo",value)))
				.buildExchange();
		exc.setProperty("a",value);
		exc.setRemoteAddrIp(value);
		return exc;
	}

	@Test
	void testHandleRequestRateLimit1Second() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().header("accept","html").build());
		exc.setResponse(Response.ResponseBuilder.newInstance().build());
		exc.setRemoteAddrIp("192.168.1.100");

		int tryLimit = 16;
		int rateLimitSeconds = 1;
		RateLimitInterceptor rli = new RateLimitInterceptor(ofSeconds(rateLimitSeconds), tryLimit);
		rli.init();

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
	void rateLimitByJWT() {
		var interceptor = new RateLimitInterceptor(ofSeconds(10), 100);
		interceptor.setKeyExpression("properties.jwt.sub");
		interceptor.init();

		var exc = new Request.Builder().buildExchange();

		// done by JwtAuthInterceptor
		var claims = new JwtClaims();
		claims.setSubject("fooman");
		exc.getProperties().put("jwt", claims);

		range(0, interceptor.getRequestLimit())
				.parallel()
				.forEach(i -> {
					try {
						assertEquals(CONTINUE, interceptor.handleRequest(exc));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

		assertEquals(RETURN, interceptor.handleRequest(exc));
	}

	@Test
	void rateLimitByJWTDifferentProperties() {
		var interceptor = new RateLimitInterceptor(ofSeconds(10), 100);
		interceptor.setKeyExpression("properties.jwt.sub");
		interceptor.init();

		var exc = new Request.Builder().buildExchange();

		// done by JwtAuthInterceptor
		var claims = Map.of(
				"sub", "fooman"
		);
		exc.getProperties().put("jwt", claims);

		range(0, interceptor.getRequestLimit())
				.parallel()
				.forEach(i -> {
					try {
						assertEquals(CONTINUE, interceptor.handleRequest(exc));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

		assertEquals(RETURN, interceptor.handleRequest(exc));
	}

	@Test
	void handleRequestRateLimit1SecondConcurrency() throws Exception
	{
		int tryLimit = 16;
		int rateLimitSeconds = 1;
		final RateLimitInterceptor rli = new RateLimitInterceptor(ofSeconds(rateLimitSeconds), tryLimit);
		rli.init();
		
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
					log.error("Error calling handleRequest: ",e);
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

	@Test
	void getClientIpWithoutXForwardedFor() {
		Exchange exc = new Request.Builder().buildExchange();
		exc.setRemoteAddrIp("10.1.2.3");

		assertEquals("10.1.2.3", new RateLimitInterceptor().getClientIp(exc));
	}

	@Test
	void getClientIpTrustXForwardedFor() {
		RateLimitInterceptor interceptor;
		interceptor = new RateLimitInterceptor();
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();

		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "171.1.1.2");

		assertEquals("171.1.1.2",interceptor.getClientIp(exc));
	}

	@Test
	void getClientIpTrustedProxyCountOfOne() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyCount(1);
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "171.1.1.2");
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "10.1.1.1");

		assertEquals("171.1.1.2",interceptor.getClientIp(exc));
	}

	@Test
	void getClientIpTrustedProxyCountOfTwo() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyCount(2);
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "192.1.1.2,	10.1.1.1, 172.0.0.1");

		assertEquals("192.1.1.2",interceptor.getClientIp(exc));
	}

	@Test
	void getClientIpTrustedProxyCountOfThree() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyCount(3);
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "192.1.1.2,	1.1.1.2,	10.1.1.1, 172.0.0.1");

		assertEquals("192.1.1.2",interceptor.getClientIp(exc));
	}


	@ParameterizedTest
	@ValueSource( strings = {"a","a,b","a,b,c"})
	void setTrustedProxyList(String v) {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyList(v);
		assertEquals(v,interceptor.getTrustedProxyList());
	}

	@Test
	void getClientIpTrustedProxyListOne() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyList("10.0.0.1");
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "	134.3.7.8,10.0.0.1");

		assertEquals("134.3.7.8",interceptor.getClientIp(exc));
	}
	@Test
	void getClientIpTrustedProxyListTwo() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyList("10.0.0.1,172.0.0.1");
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "	134.3.7.8,10.0.0.1,172.0.0.1");

		assertEquals("134.3.7.8",interceptor.getClientIp(exc));
	}

	@Test
	void getClientIpXForwardedForIsSmallerAsProxiesList() {
		RateLimitInterceptor interceptor = new RateLimitInterceptor();
		interceptor.setTrustedProxyList("10.0.0.1,172.0.0.1");
		interceptor.setTrustForwardedFor(true);

		Exchange exc = new Request.Builder().buildExchange();
		exc.getRequest().getHeader().add(X_FORWARDED_FOR, "	134.3.7.8");
		exc.setRemoteAddrIp("192.168.2.1");

		assertEquals("192.168.2.1",interceptor.getClientIp(exc));
	}

	@Test
	void getOneBeforeTrustworthyProxyTest() {
		assertEquals("c", getOneBeforeTrustworthyProxy(Arrays.asList("a","b","c"),0));
		assertEquals("b", getOneBeforeTrustworthyProxy(Arrays.asList("a","b","c"),1));
		assertEquals("a", getOneBeforeTrustworthyProxy(Arrays.asList("a","b","c"),2));
	}


	@Test
	void rateLimitInitWithoutKeyExpression() {
		new RateLimitInterceptor().init();
	}


	/*

	There may be multiple X-Forwarded-For headers present in a request.

	It is insufficient to use only one of multiple X-Forwarded-For headers.

	X-Forwarded-For: 2001:db8:85a3:8d3:1319:8a2e:370:7348

X-Forwarded-For: 203.0.113.195

X-Forwarded-For: 203.0.113.195, 2001:db8:85a3:8d3:1319:8a2e:370:7348

X-Forwarded-For: 203.0.113.195,2001:db8:85a3:8d3:1319:8a2e:370:7348,150.172.238.178


	 */

	@NotNull
	private static Exchange getExchange() throws URISyntaxException {
		final Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().get("/").header("accept","*/*").build());
		exc.setResponse(Response.ok().build());
		exc.setRemoteAddrIp("192.168.1.100");
		return exc;
	}
}
