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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.DispatchingInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Mapping;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Type.REDIRECT_TEMPORARY;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.Type.REWRITE;
import static com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor.URL_NOT_PROCESSABLE;
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

	/**
	 * The default of 'do' is derived from 'to' whenever it is read, it must not be frozen by the first read.
	 */
	@Test
	void doDefaultFollowsTo() {
		final var mapping = new Mapping();
		mapping.setFrom("^/store/(.*)");
		mapping.setTo("/shop/$1");
		assertEquals(REWRITE, mapping.getDo());
		mapping.setTo("https://example.com/shop/$1");
		assertEquals(REDIRECT_TEMPORARY, mapping.getDo());
	}

	@Test
	void longDescriptionWithDefaultDo() {
		final var mapping = new Mapping();
		mapping.setFrom("^/store/(.*)");
		mapping.setTo("/shop/$1");
		final var interceptor = new RewriteInterceptor();
		interceptor.setMappings(new ArrayList<>(List.of(mapping)));
		assertTrue(interceptor.getLongDescription().contains("REWRITE"), interceptor.getLongDescription());
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
		req.setUri("/store/x");
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
		exc.setRequest(get("/store/x").build());
		assertEquals(RETURN, prodRewriter.handleRequest(exc));

		final var json = om.readTree(exc.getResponse().getBodyAsStream());
		assertEquals(400, json.get("status").asInt());
		assertEquals("Request URL could not be processed.", json.get("title").asText());
		assertTrue(json.get("detail").asText().startsWith(URL_NOT_PROCESSABLE), json.get("detail").asText());
		assertFalse(json.toString().contains("backend.example.com"), json.toString());
		assertFalse(json.toString().toLowerCase().contains("rewrit"), json.toString());
	}


	/**
	 * A target URL with a base path is passed on as a destination with its template expression unevaluated, see issue #3497.
	 */
	@Test
	void templateTargetWithBasePath() throws URISyntaxException {
		exc.setRequest(get("/store/x").build());
		exc.setProxy(templateTargetProxy());

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertNull(exc.getResponse());
		assertEquals("http://backend.example.com:8080/api/${header.tenant}", exc.getDestinations().getFirst());
		assertEquals("/shop/v2/x", exc.getRequest().getUri());
	}

	/**
	 * A target URL with a path is used as it is, a rewriter does not change it.
	 */
	@Test
	void templateTargetWithBasePathIsNotRewritten() throws URISyntaxException {
		exc.setRequest(get("/store/x").build());
		exc.setProxy(templateTargetProxy());

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter(new Mapping("^/api/(.*)", "/v2/$1", null)).handleRequest(exc));
		assertEquals("http://backend.example.com:8080/api/${header.tenant}", exc.getDestinations().getFirst());
	}

	@Test
	void targetUrlWithPathIsNotRewritten() throws URISyntaxException {
		exc.setRequest(get("/api/x").build());
		exc.setProxy(targetUrlProxy("http://backend.example.com/api/orders"));

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter(new Mapping("^/api/(.*)", "/v2/$1", null)).handleRequest(exc));
		assertEquals("http://backend.example.com/api/orders", exc.getDestinations().getFirst());
		assertEquals("/v2/x", exc.getRequest().getUri());
	}

	/**
	 * A target URL with a path is used as it is, also when a destination replaced it, e.g. set by a balancer.
	 */
	@Test
	void replacedDestinationOfTargetUrlWithPathIsNotRewritten() throws URISyntaxException {
		exc.setRequest(get("/store/x").build());
		exc.setProxy(targetUrlProxy("http://backend.example.com/api/orders"));

		assertEquals(CONTINUE, di.handleRequest(exc));
		exc.setDestinations(new ArrayList<>(List.of("http://node.example.com/store/x")));
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertEquals("http://node.example.com/store/x", exc.getDestinations().getFirst());
		assertEquals("/shop/v2/x", exc.getRequest().getUri());
	}

	@Test
	void redirect() throws URISyntaxException {
		final var redirector = rewriter(new Mapping("^/old/(.*)", "/new/$1", "redirect-permanent"));
		exc.setRequest(get("/old/x?a=1").build());
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, redirector.handleRequest(exc));
		assertEquals(301, exc.getResponse().getStatusCode());
		assertEquals("/new/x?a=1", exc.getResponse().getHeader().getFirstValue("Location"));
	}

	/**
	 * A raw backslash must not reach the Location header, browsers resolve '/\evil.example' to another host.
	 */
	@Test
	void redirectEscapesBackslash() throws URISyntaxException {
		final var redirector = rewriter(new Mapping("^/old\\?next=([^/]+)$", "/$1", "redirect-temporary"));
		exc.setRequest(get("/").build());
		exc.getRequest().setUri("/old?next=\\evil.example"); // raw like on the wire, get() would escape it
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, redirector.handleRequest(exc));
		assertEquals("/%5Cevil.example", exc.getResponse().getHeader().getFirstValue("Location"));
	}

	/**
	 * A leading '//' of the request path is not the start of an authority, 'prefix' must not get lost.
	 */
	@Test
	void redirectKeepsLeadingDoubleSlash() throws URISyntaxException {
		exc.setRequest(get("/").build());
		exc.getRequest().setUri("//prefix/old/x");
		exc.setProxy(sp);

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter(new Mapping("^/old/(.*)", "/new/$1", "redirect-temporary")).handleRequest(exc));
		assertNull(exc.getResponse());
		assertEquals(RETURN, rewriter(new Mapping("^//prefix/old/(.*)", "/new/$1", "redirect-temporary")).handleRequest(exc));
		assertEquals("/new/x", exc.getResponse().getHeader().getFirstValue("Location"));
	}

	/**
	 * The destination of a target URL template with a base path is the unevaluated template. A redirect must not
	 * match it and put the template into the Location header, see issue #3531.
	 */
	@Test
	void redirectDoesNotMatchTemplateTarget() throws URISyntaxException {
		exc.setRequest(get("/").build());
		exc.setProxy(templateTargetProxy());

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter(new Mapping("^/api/(.*)", "/v2/$1", "redirect-temporary")).handleRequest(exc));
		assertNull(exc.getResponse());
		assertEquals("http://backend.example.com:8080/api/${header.tenant}", exc.getDestinations().getFirst());
	}

	@Test
	void redirectMatchesRequestPathWithTemplateTarget() throws URISyntaxException {
		exc.setRequest(get("/api/orders").build());
		exc.setProxy(templateTargetProxy());

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(RETURN, rewriter(new Mapping("^/api/(.*)", "/v2/$1", "redirect-temporary")).handleRequest(exc));
		assertEquals("/v2/orders", exc.getResponse().getHeader().getFirstValue("Location"));
	}

	/**
	 * A target URL with a base path is the destination of every request, a redirect must not disclose it.
	 */
	@Test
	void redirectDoesNotMatchTargetBasePath() throws URISyntaxException {
		exc.setRequest(get("/public").build());
		exc.setProxy(targetUrlProxy("http://backend.example.com/internal/orders"));

		assertEquals(CONTINUE, di.handleRequest(exc));
		assertEquals(CONTINUE, rewriter(new Mapping("^/internal/(.*)", "/v2/$1", "redirect-temporary")).handleRequest(exc));
		assertNull(exc.getResponse());
	}

	private static RewriteInterceptor rewriter(Mapping... mappings) {
		final var interceptor = new RewriteInterceptor();
		interceptor.setMappings(new ArrayList<>(List.of(mappings)));
		interceptor.init(new DummyTestRouter());
		return interceptor;
	}

	private static ServiceProxy targetUrlProxy(String url) {
		final var proxy = new ServiceProxy();
		proxy.getTarget().setUrl(url);
		proxy.init(new DummyTestRouter());
		return proxy;
	}

	private static ServiceProxy templateTargetProxy() {
		final var proxy = new ServiceProxy();
		proxy.getTarget().setUrl("http://backend.example.com:8080/api/${header.tenant}");
		proxy.init(new DummyTestRouter());
		return proxy;
	}
}
