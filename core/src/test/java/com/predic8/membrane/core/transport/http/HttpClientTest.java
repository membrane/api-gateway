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

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.http.Header.PROXY_AUTHORIZATION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePorts;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    private static final int TIMEOUT_MILLIS = 5_000;

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

    /**
     * A refused connection is the failure the RetryHandler repeats a POST for: it happens before
     * anything was written, so the request cannot have been applied (RFC 9110 §9.2.2). The next node
     * has to receive the complete body.
     */
    @Test
    void postBodyReachesNextNodeAfterConnectionRefused() throws Exception {
        final var refusedPort = closedPort();
        try (final var backend = new ServerSocket(0);
             final var executor = Executors.newSingleThreadExecutor();
             final var httpClient = new HttpClient()) {
            backend.setSoTimeout(TIMEOUT_MILLIS);
            final var receivedBody = executor.submit(() -> {
                try (final var peer = backend.accept()) {
                    peer.setSoTimeout(TIMEOUT_MILLIS);
                    final var request = new Request();
                    request.read(peer.getInputStream(), true);
                    final var body = request.getBodyAsStringDecoded();
                    peer.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                    return body;
                }
            });
            final var payload = "hello".getBytes(US_ASCII);
            final var exc = post("/foo").body(payload.length, new ByteArrayInputStream(payload)).buildExchange();
            exc.setDestinations(List.of(
                    "http://localhost:%d/foo".formatted(refusedPort),
                    "http://localhost:%d/foo".formatted(backend.getLocalPort())));

            httpClient.call(exc);

            assertEquals("hello", receivedBody.get(TIMEOUT_MILLIS, MILLISECONDS));
            assertEquals(200, exc.getResponse().getStatusCode());
        }
    }

    @Nested
    class ProxyAuthorization {

        static final String INBOUND = "Basic aW5ib3VuZA==";
        static final String PROXY_CREDENTIALS = "Basic YWxpY2U6c2VjcmV0";

        final AtomicInteger proxyRequests = new AtomicInteger();
        volatile boolean failFirstProxyRequest;
        TestRouter backend;
        TestRouter proxy;
        HttpClient proxiedClient;
        int backendPort;
        int tlsBackendPort;
        int proxyPort;

        @BeforeEach
        void setUp() throws Exception {
            final var ports = freePorts(3);
            backendPort = ports[0];
            tlsBackendPort = ports[1];
            proxyPort = ports[2];

            backend = new TestRouter();
            backend.add(createEchoProxyAuthorizationBackend(backendPort));
            final var tlsBackend = createEchoProxyAuthorizationBackend(tlsBackendPort);
            tlsBackend.setSslInboundParser(keyStoreParser());
            backend.add(tlsBackend);
            backend.start();

            proxy = new TestRouter();
            final var rule = new ProxyRule(new ProxyRuleKey(proxyPort));
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

            final var pc = new ProxyConfiguration();
            pc.setHost("localhost");
            pc.setPort(proxyPort);
            pc.setAuthentication(true);
            pc.setUsername("alice");
            pc.setPassword("secret");
            final var hcc = new HttpClientConfiguration();
            hcc.setProxy(pc);
            hcc.setSslParser(trustStoreParser());
            hcc.setBaseLocation("");
            proxiedClient = new HttpClient(hcc);
        }

        @AfterEach
        void tearDown() {
            try {
                if (proxiedClient != null)
                    proxiedClient.close();
            } finally {
                try {
                    if (proxy != null)
                        proxy.stop();
                } finally {
                    if (backend != null)
                        backend.stop();
                }
            }
        }

        @Test
        void proxyCredentialsOfPlainAttemptAreNotSentThroughTunnelOfRetry() throws Exception {
            final var exc = callPlainThenTls(get("/foo"));

            assertEquals(2, proxyRequests.get());
            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void inboundProxyAuthorizationIsSentThroughTunnelOfRetry() throws Exception {
            final var exc = callPlainThenTls(get("/foo").header(PROXY_AUTHORIZATION, INBOUND));

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(INBOUND, exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void proxyCredentialsAreSentToProxyForPlainHttp() throws Exception {
            final var exc = get("http://localhost:%d/foo".formatted(backendPort))
                    .header(PROXY_AUTHORIZATION, INBOUND)
                    .buildExchange();
            proxiedClient.call(exc);

            assertEquals(1, proxyRequests.get());
            assertEquals(PROXY_CREDENTIALS, exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void proxyCredentialsAreRemovedFromRequestAfterProxiedCall() throws Exception {
            final var exc = get("http://localhost:%d/foo".formatted(backendPort)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(1, proxyRequests.get());
            assertNull(exc.getRequest().getHeader().getFirstValue(PROXY_AUTHORIZATION));
        }

        @Test
        void inboundProxyAuthorizationIsRestoredOnRequestAfterProxiedCall() throws Exception {
            final var exc = get("http://localhost:%d/foo".formatted(backendPort))
                    .header(PROXY_AUTHORIZATION, INBOUND)
                    .buildExchange();
            proxiedClient.call(exc);

            assertEquals(1, proxyRequests.get());
            assertEquals(INBOUND, exc.getRequest().getHeader().getFirstValue(PROXY_AUTHORIZATION));
        }

        private Exchange callPlainThenTls(Request.Builder builder) throws Exception {
            failFirstProxyRequest = true;
            final var exc = builder.buildExchange();
            exc.setDestinations(List.of(
                    "http://localhost:%d/foo".formatted(backendPort),
                    "https://localhost:%d/foo".formatted(tlsBackendPort)));
            proxiedClient.call(exc);
            return exc;
        }

        private static ServiceProxy createEchoProxyAuthorizationBackend(int port) {
            final var sp = new ServiceProxy(new ServiceProxyKey(port), null, 0);
            sp.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    final var proxyAuthorization = exc.getRequest().getHeader().getFirstValue(PROXY_AUTHORIZATION);
                    exc.setResponse(Response.ok(proxyAuthorization != null ? proxyAuthorization : "").build());
                    return RETURN;
                }
            });
            return sp;
        }

        private static SSLParser keyStoreParser() {
            final var ssl = new SSLParser();
            ssl.setKeyStore(new KeyStore());
            ssl.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
            ssl.getKeyStore().setKeyPassword("secret");
            return ssl;
        }

        private static SSLParser trustStoreParser() {
            final var ssl = new SSLParser();
            ssl.setTrustStore(new TrustStore());
            ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
            ssl.getTrustStore().setPassword("secret");
            ssl.setEndpointIdentificationAlgorithm(""); // the certificate was not issued for 'localhost'
            return ssl;
        }
    }

    /**
     * A port nobody listens on, so connecting to it is refused.
     */
    private static int closedPort() throws IOException {
        try (final var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
