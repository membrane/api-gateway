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
import org.mockito.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.PROXY_AUTHORIZATION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

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

        static final int BACKEND_PORT = 3048;
        static final int PROXY_PORT = 3068;

        final AtomicInteger proxyRequests = new AtomicInteger();
        TestRouter backend;
        TestRouter proxy;
        HttpClient proxiedClient;

        @BeforeEach
        void setUp() throws Exception {
            backend = new TestRouter();
            var sp = new ServiceProxy(new ServiceProxyKey(BACKEND_PORT), null, 0);
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
            var rule = new ProxyRule(new ProxyRuleKey(PROXY_PORT));
            rule.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    proxyRequests.incrementAndGet();
                    return super.handleRequest(exc);
                }
            });
            proxy.add(rule);
            proxy.start();

            var pc = new ProxyConfiguration();
            pc.setHost("localhost");
            pc.setPort(PROXY_PORT);
            pc.setAuthentication(true);
            pc.setUsername("alice");
            pc.setPassword("secret");
            pc.setNoProxy("localhost");
            var hcc = new HttpClientConfiguration();
            hcc.setProxy(pc);
            proxiedClient = new HttpClient(hcc);
        }

        @AfterEach
        void tearDown() {
            proxiedClient.close();
            proxy.stop();
            backend.stop();
        }

        @Test
        void bypassedHostIsCalledDirectlyWithoutProxyCredentials() throws Exception {
            var exc = get("http://localhost:%d/foo".formatted(BACKEND_PORT)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(0, proxyRequests.get());
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());
        }

        @Test
        void otherHostsGoThroughTheProxy() throws Exception {
            var exc = get("http://127.0.0.1:%d/foo".formatted(BACKEND_PORT)).buildExchange();
            proxiedClient.call(exc);

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(1, proxyRequests.get());
        }
    }
}