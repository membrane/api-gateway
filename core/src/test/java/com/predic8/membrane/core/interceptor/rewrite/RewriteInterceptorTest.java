/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.rewrite;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.MessageUtil;
public class RewriteInterceptorTest {

	private RewriteInterceptor rewriter;
	private Exchange exc;

	private DispatchingInterceptor di;
	private ServiceProxy sp;

	@BeforeEach
	public void setUp() throws Exception {
		HttpRouter router = new HttpRouter();

		di = new DispatchingInterceptor();
		di.init(router);

		sp = new ServiceProxy(new ServiceProxyKey(80, null), "www.predic8.de", 80);
		sp.init(router);

		exc = new Exchange(null);


		rewriter = new RewriteInterceptor();
		List<Mapping> mappings = new ArrayList<>();
		mappings.add( new Mapping("/buy/(.*)/(.*)", "/buy?item=$1&amount=$2", null));
		mappings.add( new Mapping("^/store/(.*)", "/shop/v2/$1",null));
		rewriter.setMappings(mappings);
		rewriter.init(router);
	}

	@Test
	void testRewriteWithoutTarget() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/3"));
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/buy?item=banana&amount=3", exc.getDestinations().get(0));
	}

	@Test
	void testRewrite() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/3"));
		exc.setRule(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/buy?item=banana&amount=3", exc.getDestinations().get(0));
	}

	@Test
	void storeSample() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("https://api.predic8.de/store/products/"));
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("https://api.predic8.de/shop/v2/products/", exc.getDestinations().get(0));
	}

	@Test
	void invalidURI() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/%"));
		exc.setRule(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, rewriter.handleRequest(exc));
		assertEquals(
			"""
				{
				  "type" : "http://membrane-api.io/error/uri-parser",
				  "title" : "This URL does not follow the URI specification. Confirm the validity of the provided URL."
				}""",
				exc.getResponse().getBodyAsStringDecoded()
		);
	}
}
