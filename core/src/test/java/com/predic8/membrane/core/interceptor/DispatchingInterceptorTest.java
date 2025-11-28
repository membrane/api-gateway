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

import tools.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class DispatchingInterceptorTest {

	static  final ObjectMapper om = new ObjectMapper();

	DispatchingInterceptor dispatcher;
	ServiceProxy serviceProxy;

	Exchange exc;

	@BeforeEach
	void setUp() {
		Router router = new Router();
		dispatcher = new DispatchingInterceptor();
		dispatcher.init(router);
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

	@Nested
	class ErrorHandling {

		@Test
		void invalidUriErrorMessage() throws Exception {
			APIProxy api = new APIProxy();
			api.setTarget(new AbstractServiceProxy.Target() {{
				setHost("localhost");
			}});

			Exchange exchange = get("/dummy").buildExchange();
			exchange.getRequest().setUri("/foo{invalidUri}");
			exchange.setProxy(api);

            assertEquals(ABORT,  dispatcher.handleRequest(exchange));

			Response r = exchange.getResponse();
			assertEquals(400, r.getStatusCode());

			JsonNode jn = om.readTree(r.getBodyAsStringDecoded());
			assertEquals("Invalid request path", jn.get(TITLE).asText());
			assertEquals("https://membrane-api.io/problems/user", jn.get(TYPE).asText());
			assertEquals(4, jn.get("index").asInt());
			assertEquals("/foo{invalidUri}", jn.get("path").asText());
		}
	}
}