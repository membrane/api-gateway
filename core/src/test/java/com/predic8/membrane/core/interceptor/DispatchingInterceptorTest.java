/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.net.URL;

import static com.predic8.membrane.core.exceptions.ProblemDetails.TITLE;
import static com.predic8.membrane.core.exceptions.ProblemDetails.TYPE;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.router.DummyTestRouter.productionRouter;
import static com.predic8.membrane.core.util.URIFactory.ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY;
import static org.junit.jupiter.api.Assertions.*;

class DispatchingInterceptorTest {

	static  final ObjectMapper om = new ObjectMapper();

	DispatchingInterceptor dispatcher;
	ServiceProxy serviceProxy;
	Exchange exc;
	Router defaultRouter;
	Router routerAllowIllegal;

	@BeforeEach
	void setUp() {
		routerAllowIllegal = new DefaultRouter();
		routerAllowIllegal.getConfiguration().setUriFactory(ALLOW_ILLEGAL_CHARACTERS_URI_FACTORY);
		defaultRouter = new DefaultRouter();
		dispatcher = new DispatchingInterceptor();
		dispatcher.init(defaultRouter);
		exc = new Exchange(null);
		serviceProxy = new ServiceProxy(new ServiceProxyKey("localhost", ".*", ".*", 3011), "thomas-bayer.com", 80);
	}

	@Test
	void testServiceProxy() throws Exception {
		exc.setProxy(serviceProxy);
		addRequest("/axis2/services/BLZService?wsdl");

		assertEquals(CONTINUE, dispatcher.handleRequest(exc));

		URL url = new URL(exc.getDestinations().getFirst());
		assertEquals(80, url.getPort());
		assertEquals("thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	@Test
	void proxyRuleHttp() throws Exception {
		exc.setRequest(get("/dummy").build());
		exc.getRequest().setUri("http://www.thomas-bayer.com:80/axis2/services/BLZService?wsdl");
		exc.setProxy(getProxyRule());

		assertEquals(CONTINUE, dispatcher.handleRequest(exc));

		URL url = new URL(exc.getDestinations().getFirst());

		assertEquals(80, url.getPort());
		assertEquals("www.thomas-bayer.com", url.getHost());
		assertEquals("/axis2/services/BLZService?wsdl", url.getFile());
	}

	private ProxyRule getProxyRule() {
		return new ProxyRule(new ProxyRuleKey(3090));
	}

    @Test
    void getAddressFromTargetElementTargetWithHostAndPort() throws Exception {
		exc.setProxy(serviceProxy);
		addRequest("/foo");
		assertEquals("http://thomas-bayer.com:80/foo", getGetAddressFromTargetElement());
    }

	@ParameterizedTest
	@ValueSource(strings = {"//x/admin", "//x//admin?a=b", "/x//admin"})
	@DisplayName("A path with duplicate slashes is forwarded unchanged, a leading // is not taken as a host")
	void duplicateSlashesAreForwardedUnchanged(String uri) throws Exception {
		exc.setProxy(serviceProxy);
		exc.setRequest(new Request.Builder().method("GET").uri(uri).build());
		assertEquals("http://thomas-bayer.com:80" + uri, getGetAddressFromTargetElement());
	}

	@ParameterizedTest
	@ValueSource(strings = {"//x/admin", "/x//admin"})
	@DisplayName("A path with duplicate slashes is appended unchanged to a target URL")
	void duplicateSlashesAreAppendedUnchangedToTargetUrl(String uri) throws Exception {
		serviceProxy.getTarget().setUrl("http://api.predic8.de");
		serviceProxy.getTarget().setHost(null);
		serviceProxy.getTarget().setPort(-1);
		exc.setProxy(serviceProxy);
		exc.setRequest(new Request.Builder().method("GET").uri(uri).build());
		assertEquals("http://api.predic8.de" + uri, getGetAddressFromTargetElement());
	}

	@Test
	@DisplayName("A leading // in the path does not replace the host of the target URL")
	void leadingDoubleSlashDoesNotReplaceTargetHost() throws Exception {
		serviceProxy.getTarget().setUrl("http://api.predic8.de");
		serviceProxy.getTarget().setHost(null);
		serviceProxy.getTarget().setPort(-1);
		exc.setProxy(serviceProxy);
		exc.setRequest(new Request.Builder().method("GET").uri("//attacker.example.com/x").build());
		assertTrue(getGetAddressFromTargetElement().startsWith("http://api.predic8.de/"), getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithURL() throws Exception {
		serviceProxy.getTarget().setUrl("http://api.predic8.de");
		serviceProxy.getTarget().setHost(null);
		serviceProxy.getTarget().setPort(-1);
		exc.setProxy(serviceProxy);
		addRequest("/foo");
		assertEquals("http://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithURLHTTPS() throws Exception {
		serviceProxy.getTarget().setUrl("https://api.predic8.de");
		exc.setProxy(serviceProxy);
		addRequest("/foo");
		assertEquals("https://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithSlash() throws Exception {
		serviceProxy.getTarget().setUrl("https://api.predic8.de/");
		exc.setProxy(serviceProxy);
		addRequest("/foo");
		assertEquals("https://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Test
	void getAddressFromTargetElementTargetWithPath() throws Exception {
		serviceProxy.getTarget().setUrl("https://api.predic8.de/baz");
		exc.setProxy(serviceProxy);
		addRequest("/foo");
		assertEquals("https://api.predic8.de/baz", getGetAddressFromTargetElement());
	}

	@Test
	@DisplayName("getAddressFromTargetElement HostAndPort AbsoluteURL")
	void getAddressFromTargetElementTarget_hostAndPort_absoluteURL() throws Exception {
		exc.setProxy(serviceProxy);
		addRequest("https://localhost:8888/foo");
		assertEquals("http://thomas-bayer.com:80/foo", getGetAddressFromTargetElement());
	}

	@Test
	@DisplayName("getAddressFromTargetElement URL AbsoluteURL")
	void getAddressFromTargetElementWith_URL_absoluteURL() throws Exception {
		serviceProxy.getTarget().setUrl("http://api.predic8.de");
		serviceProxy.getTarget().setHost(null);
		serviceProxy.getTarget().setPort(-1);
		exc.setProxy(serviceProxy);
		exc.setRequest(get("https://localhost:8888/foo").build());
		exc.setOriginalRequestUri("https://localhost:8888/foo");
		assertEquals("http://api.predic8.de/foo", getGetAddressFromTargetElement());
	}

	@Nullable
	private String getGetAddressFromTargetElement() throws Exception {
		return dispatcher.getAddressFromTargetElement( exc);
	}

	private void addRequest(String uri) throws Exception {
		exc.setRequest(get(uri).build());
	}

	@Test
	void initWithAllowIllegalAndURLExpression() {
		var api = new APIProxy();
		api.setTarget(new Target() {{
			setUrl("https://${property.host}:8080"); // Has illegal characters $ { } in base path
		}});

		assertThrows(ConfigurationException.class, ()-> api.init(routerAllowIllegal));
	}

	@Nested
	class ErrorHandling {

		@Test
		void invalidUriErrorMessage() throws Exception {
			var exc = getExchange();
			assertEquals(ABORT,  dispatcher.handleRequest(exc));

			var r = exc.getResponse();
			assertEquals(400, r.getStatusCode());

			var jn = om.readTree(r.getBodyAsStringDecoded());
			assertTrue(jn.get(TITLE).asText().contains("invalid character"));
			assertEquals("https://membrane-api.io/problems/user", jn.get(TYPE).asText());
			assertEquals("/foo{invalidUri}", jn.get("path").asText());
		}

		@Test
		void invalidUriErrorMessageProduction() throws Exception {
			var exc = getExchange();
			dispatcher.init(productionRouter());
			assertEquals(ABORT,  dispatcher.handleRequest(exc));

			var r = exc.getResponse();
			assertEquals(400, r.getStatusCode());

			var jn = om.readTree(r.getBodyAsStringDecoded());
			assertTrue(jn.get(TITLE).asText().contains("invalid character"));
			assertEquals("https://membrane-api.io/problems/user", jn.get(TYPE).asText());
			assertFalse(jn.has("path"));
		}

		@Test
		void validPathWithUnderscore() throws Exception {
			// An API must be set on the exchange, otherwise in the interceptor a URL is not parsed
			var api = new APIProxy();
			api.setTarget(new Target() {{
				setUrl("http://dummy/_tb/?test=21");
			}});
			var exc = get("/_tb/?test=21").buildExchange();
			exc.setProxy(api);
			assertEquals(CONTINUE,  dispatcher.handleRequest(exc));
		}

		private @NotNull Exchange getExchange() throws URISyntaxException {
			// Valid URI to pass first check
			var exc = get("/dummy").buildExchange();

			// Invalid for test
			exc.getRequest().setUri("/foo{invalidUri}");
			exc.setProxy(getApiProxy());
			return exc;
		}

		private @NotNull APIProxy getApiProxy() {
			var api = new APIProxy();
			api.setTarget(new Target() {{
				setHost("localhost");
			}});
			return api;
		}
	}
}