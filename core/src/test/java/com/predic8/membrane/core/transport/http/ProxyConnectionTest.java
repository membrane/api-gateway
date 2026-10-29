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

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.exchange.Exchange.SSL_CONTEXT;
import static com.predic8.membrane.core.http.Request.METHOD_CONNECT;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the {@link HttpClient} reuses its connection to an HTTP proxy.
 */
class ProxyConnectionTest {

	private HttpClient client;

	private static HttpClient createClient(int proxyPort) {
		final var proxyConfiguration = new ProxyConfiguration();
		proxyConfiguration.setHost("localhost");
		proxyConfiguration.setPort(proxyPort);
		final var configuration = new HttpClientConfiguration();
		configuration.setProxy(proxyConfiguration);
		return new HttpClient(configuration);
	}

	private int getNumberInPool() {
		return client.getConnectionFactory().getConnectionManager().getNumberInPool();
	}

	/**
	 * Plain HTTP: the client sends the absolute URL to the proxy, so one connection to the proxy serves every target.
	 */
	@Nested
	class PlainHttp {

		private static final int PROXY_PORT = 3048;

		private final Set<Integer> proxyConnections = Collections.synchronizedSet(new HashSet<>()); // hashcodes of all connections the proxy received
		private Router proxy;

		@BeforeEach
		void setUp() throws Exception {
			proxy = new TestRouter();
			final var sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", PROXY_PORT), "", -1);
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

			client = createClient(PROXY_PORT);
		}

		@AfterEach
		void tearDown() {
			client.close();
			proxy.stop();
		}

		private int issueRequest(String url) throws Exception {
			final var exchange = get(url).buildExchange();
			client.call(exchange);
			exchange.getResponse().readBody();
			return exchange.getResponse().getStatusCode();
		}

		@Test
		void connectionToProxyIsReused() throws Exception {
			assertEquals(200, issueRequest("http://backend.example.com/"));
			assertEquals(200, issueRequest("http://backend.example.com/"));

			assertEquals(1, proxyConnections.size());
			assertEquals(1, getNumberInPool());
		}

		@Test
		void connectionToProxyIsReusedForDifferentTargets() throws Exception {
			assertEquals(200, issueRequest("http://backend1.example.com/"));
			assertEquals(200, issueRequest("http://backend2.example.com:8080/"));

			assertEquals(1, proxyConnections.size());
			assertEquals(1, getNumberInPool());
		}
	}

	/**
	 * HTTPS: the client tunnels through the proxy using CONNECT, so each tunnel is bound to one target.
	 */
	@Nested
	class Https {

		private static final int PROXY_PORT = 3068;
		private static final int BACKEND_PORT = 3069;

		private final AtomicInteger connectRequests = new AtomicInteger();
		private Router proxy;
		private Router backend;
		private SSLProvider clientSSLContext;

		@BeforeEach
		void setUp() throws Exception {
			backend = new TestRouter();
			final var sp = new ServiceProxy(new ServiceProxyKey(BACKEND_PORT), null, 0);
			sp.setSslInboundParser(createKeyStoreParser());
			sp.getFlow().add(new AbstractInterceptor() {
				@Override
				public Outcome handleRequest(Exchange exc) {
					exc.setResponse(ok("OK.").build());
					return RETURN;
				}
			});
			backend.add(sp);
			backend.start();

			proxy = new TestRouter();
			final var proxyRule = new ProxyRule(new ProxyRuleKey(PROXY_PORT));
			proxyRule.getFlow().add(new AbstractInterceptor() {
				@Override
				public Outcome handleRequest(Exchange exc) {
					if (METHOD_CONNECT.equals(exc.getRequest().getMethod()))
						connectRequests.incrementAndGet();
					return CONTINUE;
				}
			});
			proxy.add(proxyRule);
			proxy.start();

			clientSSLContext = new StaticSSLContext(createTrustStoreParser(), new ResolverMap(), null);
			client = createClient(PROXY_PORT);
		}

		@AfterEach
		void tearDown() {
			client.close();
			proxy.stop();
			backend.stop();
		}

		private static SSLParser createKeyStoreParser() {
			final var ssl = new SSLParser();
			ssl.setKeyStore(new KeyStore());
			ssl.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
			ssl.getKeyStore().setKeyPassword("secret");
			return ssl;
		}

		private static SSLParser createTrustStoreParser() {
			final var ssl = new SSLParser();
			ssl.setTrustStore(new TrustStore());
			ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
			ssl.getTrustStore().setPassword("secret");
			ssl.setEndpointIdentificationAlgorithm(""); // the certificate was not issued for 'localhost'
			return ssl;
		}

		private int issueRequest() throws Exception {
			final var exchange = get("https://localhost:%d/".formatted(BACKEND_PORT)).buildExchange();
			exchange.setProperty(SSL_CONTEXT, clientSSLContext);
			client.call(exchange);
			exchange.getResponse().readBody();
			return exchange.getResponse().getStatusCode();
		}

		@Test
		void tunnelThroughProxyIsReused() throws Exception {
			assertEquals(200, issueRequest());
			assertEquals(200, issueRequest());

			assertEquals(1, connectRequests.get());
			assertEquals(1, getNumberInPool());
		}
	}
}
