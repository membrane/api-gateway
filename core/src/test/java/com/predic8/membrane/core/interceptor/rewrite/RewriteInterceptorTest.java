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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.URL_NOT_PROCESSABLE;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Type.*;
import static org.junit.jupiter.api.Assertions.*;
public class RewriteInterceptorTest {

	private final static ObjectMapper om = new ObjectMapper();

	private RewriteInterceptor rewriter;
	private Exchange exc;

	private DispatchingInterceptor di;
	private ServiceProxy sp;

	@BeforeEach
	void setUp() {
		DummyTestRouter router = new DummyTestRouter();

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
		exc.setRequest(get("/buy/banana/3").build());
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	@Test
	void testRewrite() throws URISyntaxException {
		exc.setRequest(get("/buy/banana/3").build());
		exc.setOriginalRequestUri("/buy/banana/3");
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/buy?item=banana&amount=3", exc.getDestinations().getFirst());
	}

	/**
	 * A '://' inside the query must not make the rewritten path count as absolute, see issue #3437.
	 */
	@Test
	void rewriteKeepsTargetWhenQueryContainsSchemeSeparator() throws URISyntaxException {
		final var uri = "/store/products?ns=http://example.com/foo/bar";
		exc.setRequest(get(uri).build());
		exc.setOriginalRequestUri(uri);
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://www.predic8.de:80/shop/v2/products?ns=http://example.com/foo/bar", exc.getDestinations().getFirst());
	}

	/**
	 * A relative destination has no authority, so a '//' in its query must not be taken for one.
	 */
	@Test
	void rewriteWithoutTargetIgnoresSchemeSeparatorInQuery() throws URISyntaxException {
		exc.setRequest(get("/store/products?ns=http://example.com/foo/bar").build());
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/shop/v2/products?ns=http://example.com/foo/bar", exc.getDestinations().getFirst());
	}

	/**
	 * Only an absolute 'to' defaults to a redirect, a '://' in its query does not.
	 */
	@Test
	void doDefaultIgnoresSchemeSeparatorInQuery() {
		assertEquals(REWRITE, new Mapping("^/store/(.*)", "/shop?ns=http://example.com/$1", null).getDo());
		assertEquals(REDIRECT_TEMPORARY, new Mapping("^/store/(.*)", "https://example.com/shop/$1", null).getDo());
	}

	@Test
	void storeSample() throws URISyntaxException {
		exc.setRequest(get("/store/products/").build());
		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("/shop/v2/products/", exc.getDestinations().getFirst());
	}

	@Test
	void invalidURI() throws Exception {
		exc.setProxy(sp);
		exc.getDestinations().add("/buy/banana/%");
		var req = new Request();
		req.getHeader().setContentType(APPLICATION_JSON);
		exc.setRequest(req);
		assertEquals(RETURN, rewriter.handleRequest(exc));

		JsonNode json = om.readTree(exc.getResponse().getBodyAsStream());

		assertEquals("https://membrane-api.io/problems/user/path",json.get("type").asText());
		assertEquals("Request URL could not be processed.",json.get("title").asText());
		assertEquals(URL_NOT_PROCESSABLE, json.get("detail").asText());
		assertTrue(json.get("path").asText().contains("/buy/banana/%"));
		assertTrue(json.get("component").asText().contains("rewrite"));
		assertTrue(json.has("reason"));
	}

	@Test
	void invalidURIInProduction() throws Exception {
		final var prodRewriter = new RewriteInterceptor();
		prodRewriter.setMappings(new ArrayList<>(List.of(new Mapping("^/store/(.*)", "/shop/$1", null))));
		prodRewriter.init(DummyTestRouter.productionRouter());

		exc.setProxy(sp);
		exc.getDestinations().add("http://backend.example.com/buy/banana/%");
		exc.setRequest(new Request());
		assertEquals(RETURN, prodRewriter.handleRequest(exc));

		final var json = om.readTree(exc.getResponse().getBodyAsStream());
		assertEquals(400, json.get("status").asInt());
		assertEquals("Request URL could not be processed.", json.get("title").asText());
		assertTrue(json.get("detail").asText().startsWith(URL_NOT_PROCESSABLE), json.get("detail").asText());
		assertFalse(json.toString().contains("backend.example.com"), json.toString());
		assertFalse(json.toString().toLowerCase().contains("rewrit"), json.toString());
	}
}
