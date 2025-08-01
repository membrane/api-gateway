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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
public class RewriteInterceptorTest {

	private final static ObjectMapper om = new ObjectMapper();

	private RewriteInterceptor rewriter;
	private Exchange exc;

	private DispatchingInterceptor di;
	private ServiceProxy sp;

	@BeforeEach
	public void setUp() {
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
	void testRewriteWithoutTarget() throws URISyntaxException {
		exc.setRequest(new Request.Builder().get("/buy/banana/3").build());
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	@Test
	void testRewrite() throws URISyntaxException {
		exc.setRequest(new Request.Builder().get("/buy/banana/3").build());
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	@Test
	void storeSample() throws URISyntaxException {
		exc.setRequest(new Request.Builder().get("https://api.predic8.de/store/products/").build());
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		//dont work!!!!
		assertEquals("https://api.predic8.de/shop/v2/products/", exc.getDestinations().getFirst());
	}

	@Test
	void invalidURI() throws Exception {
		//dont work!!!!
		exc.setRequest(new Request.Builder().get("/buy/banana/%").build());
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, rewriter.handleRequest(exc));

		JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());

		assertEquals("https://membrane-api.io/problems/user/path",json.get("type").asText());
		assertEquals("The path does not follow the URI specification. Confirm the validity of the provided URL.",json.get("title").asText());
		assertTrue(json.get("detail").asText().contains("http://www.predic8.de:80/buy/banana/%"));
	}
}
