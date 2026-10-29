/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the {@link HttpClient} reuses its connection to an HTTP proxy for plain HTTP requests.
 */
class ProxyConnectionTest {

	private static final int PROXY_PORT = 3023;

	private final Set<Integer> proxyConnections = Collections.synchronizedSet(new HashSet<>()); // hashcodes of all connections the proxy received
	private Router proxy;
	private HttpClient client;

	@BeforeEach
	void setUp() throws Exception {
		proxy = new TestRouter();
		ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", PROXY_PORT), "", -1);
		sp.getFlow().add(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) {
				try {
					exc.getRequest().readBody();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				exc.setResponse(ok("OK.").build());
				proxyConnections.add(((HttpServerHandler) exc.getHandler()).getSrcOut().hashCode());
				return RETURN;
			}
		});
		proxy.add(sp);
		proxy.start();

		ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
		proxyConfiguration.setHost("localhost");
		proxyConfiguration.setPort(PROXY_PORT);
		HttpClientConfiguration configuration = new HttpClientConfiguration();
		configuration.setProxy(proxyConfiguration);
		client = new HttpClient(configuration);
	}

	@AfterEach
	void tearDown() {
		client.close();
		proxy.stop();
	}

	private int issueRequest(String url) throws Exception {
		Exchange exchange = get(url).buildExchange();
		client.call(exchange);
		exchange.getResponse().readBody();
		return exchange.getResponse().getStatusCode();
	}

	@Test
	void connectionToProxyIsReused() throws Exception {
		assertEquals(200, issueRequest("http://backend.example.com/"));
		assertEquals(200, issueRequest("http://backend.example.com/"));

		assertEquals(1, proxyConnections.size());
		assertEquals(1, client.getConnectionFactory().getConnectionManager().getNumberInPool());
	}

	@Test
	void connectionToProxyIsReusedForDifferentTargets() throws Exception {
		assertEquals(200, issueRequest("http://backend1.example.com/"));
		assertEquals(200, issueRequest("http://backend2.example.com:8080/"));

		assertEquals(1, proxyConnections.size());
		assertEquals(1, client.getConnectionFactory().getConnectionManager().getNumberInPool());
	}
}
