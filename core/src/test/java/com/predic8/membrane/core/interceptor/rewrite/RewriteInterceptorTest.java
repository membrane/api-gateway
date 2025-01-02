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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
public class RewriteInterceptorTest {

	private final static ObjectMapper om = new ObjectMapper();

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
		assertEquals("/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	@Test
	void testRewrite() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/3"));
		exc.setRule(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	@Test
	void storeSample() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("https://api.predic8.de/store/products/"));
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("https://api.predic8.de/shop/v2/products/", exc.getDestinations().getFirst());
	}

	@Test
	void invalidURI() throws Exception {
		exc.setRequest(MessageUtil.getGetRequest("/buy/banana/%"));
		exc.setRule(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, rewriter.handleRequest(exc));

		JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());

		assertEquals("https://membrane-api.io/error/user/path",json.get("type").asText());
		assertEquals("The path does not follow the URI specification. Confirm the validity of the provided URL.",json.get("title").asText());
		assertTrue(json.get("detail").asText().contains("http://www.predic8.de:80/buy/banana/%"));
	}
}
