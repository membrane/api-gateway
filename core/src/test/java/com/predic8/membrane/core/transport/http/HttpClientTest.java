/* Copyright 2025 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.PROXY_AUTHORIZATION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HttpClientTest {

    HttpClient client;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @Test
    void init() throws Exception {
        var exc = get("/foo").buildExchange();
        client.adjustHostHeader(exc,  new HostColonPort(false,HttpClient.getHostColonPort( "https://example.com")));
        assertEquals("example.com:443",exc.getRequest().getHeader().getHost());
    }

    @Test
    void setRequestURISimple() throws Exception {
        var request = get("/bar").build();
        client.setRequestURI(request, "https://predic8.de/foo", Mockito.mock(Connection.class));
        assertEquals("/foo", request.getUri());
    }

    @Test
    void setRequestURIWithoutPath() throws Exception {
        var request = get("/bar").build();
        client.setRequestURI(request, "https://predic8.de", Mockito.mock(Connection.class));
        assertEquals("/", request.getUri());
    }

    @Nested
    class NoProxy {

        final AtomicInteger proxyRequests = new AtomicInteger();
        volatile boolean failFirstProxyRequest;
        TestRouter backend;
        TestRouter proxy;
        HttpClient proxiedClient;
        int backendPort;
        int proxyPort;

        @BeforeEach
        void setUp() throws Exception {
            final var ports = freePorts(2);
            backendPort = ports[0];
            proxyPort = ports[1];

            backend = new TestRouter();
            var sp = new ServiceProxy(new ServiceProxyKey(backendPort), null, 0);
            sp.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    var proxyAuthorization = exc.getRequest().getHeader().getFirstValue(PROXY_AUTHORIZATION);
                    exc.setResponse(Response.ok(proxyAuthorization != null ? proxyAuthorization : "").build());
                    return RETURN;
                }
            });
            backend.add(sp);
            backend.start();

            proxy = new TestRouter();
            var rule = new ProxyRule(new ProxyRuleKey(proxyPort));
            rule.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    if (proxyRequests.incrementAndGet() == 1 && failFirstProxyRequest) {
                        exc.setResponse(Response.statusCode(408).build());
                        return RETURN;
                    }
                    return super.handleRequest(exc);
                }
            });
            proxy.add(rule);
            proxy.start();

            proxiedClient = createProxiedClient("localhost");
        }

        private HttpClient createProxiedClient(String noProxy) {
            var pc = new ProxyConfiguration();
            pc.setHost("localhost");
            pc.setPort(proxyPort);
            pc.setAuthentication(true);
            pc.setUsername("alice");
            pc.setPassword("secret");
            pc.setNoProxy(noProxy);
            var hcc = new HttpClientConfiguration();
            hcc.setProxy(pc);
            return new HttpClient(hcc);
        }

        @AfterEach
        void tearDown() {
            proxiedClient.close();
            proxy.stop();
            backend.stop();
        }

        @Test
        void bypassedHostIsCalledDirectlyWithoutProxyCredentials() throws Exception {
            var exc = get("http://localhost:%d/foo".formatted(backendPort)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(0, proxyRequests.get());
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void otherHostsGoThroughTheProxy() throws Exception {
            var exc = get("http://127.0.0.1:%d/foo".formatted(backendPort)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(1, proxyRequests.get());
        }

        @Test
        void retryToBypassedHostDropsProxyCredentialsOfProxiedAttempt() throws Exception {
            failFirstProxyRequest = true;
            final var exc = get("/foo").buildExchange();
            exc.setDestinations(List.of(
                    "http://127.0.0.1:%d/foo".formatted(backendPort),
                    "http://localhost:%d/foo".formatted(backendPort)));
            proxiedClient.call(exc);

            assertEquals(1, proxyRequests.get());
            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void inboundProxyAuthorizationIsKeptForBypassedHost() throws Exception {
            final var exc = get("http://localhost:%d/foo".formatted(backendPort))
                    .header(PROXY_AUTHORIZATION, "Basic aW5ib3VuZA==")
                    .buildExchange();
            proxiedClient.call(exc);

            assertEquals("Basic aW5ib3VuZA==", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void inboundProxyAuthorizationEqualToConfiguredCredentialsIsKeptForBypassedHost() throws Exception {
            final var exc = get("http://localhost:%d/foo".formatted(backendPort))
                    .header(PROXY_AUTHORIZATION, "Basic YWxpY2U6c2VjcmV0")
                    .buildExchange();
            proxiedClient.call(exc);

            assertEquals(0, proxyRequests.get());
            assertEquals("Basic YWxpY2U6c2VjcmV0", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void inboundProxyAuthorizationIsRestoredForBypassedHostAfterProxiedAttempt() throws Exception {
            failFirstProxyRequest = true;
            final var exc = get("/foo").header(PROXY_AUTHORIZATION, "Basic aW5ib3VuZA==").buildExchange();
            exc.setDestinations(List.of(
                    "http://127.0.0.1:%d/foo".formatted(backendPort),
                    "http://localhost:%d/foo".formatted(backendPort)));
            proxiedClient.call(exc);

            assertEquals(1, proxyRequests.get());
            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals("Basic aW5ib3VuZA==", exc.getResponse().getBodyAsStringDecoded());
        }

        @ParameterizedTest
        @ValueSource(strings = {"::1", "::/96"})
        void bypassedIpv6HostIsCalledDirectly(String noProxy) throws Exception {
            assumeTrue(isIpv6LoopbackAvailable(), "IPv6 loopback is not available");
            proxiedClient.close();
            proxiedClient = createProxiedClient(noProxy);

            final var exc = get("http://[::1]:%d/foo".formatted(backendPort)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(0, proxyRequests.get());
        }

        private static boolean isIpv6LoopbackAvailable() {
            try (var ignored = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }
}