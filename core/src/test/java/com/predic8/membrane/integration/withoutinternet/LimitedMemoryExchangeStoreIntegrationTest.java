/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.integration.withoutinternet;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.integration.withinternet.LargeBodyTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.http.Header.CHUNKED;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;
import static com.predic8.membrane.core.http.Request.Builder;
import static com.predic8.membrane.core.http.Request.post;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitedMemoryExchangeStoreIntegrationTest {
    private LimitedMemoryExchangeStore lmes;
    private DefaultRouter router;
    private DefaultRouter router2;
    private HttpClientConfiguration hcc;
    private final AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        lmes = new LimitedMemoryExchangeStore();
        lmes.setMaxSize(500_000);
        // streaming only works when no retry can happen, i.e. retries = 0
        hcc = new HttpClientConfiguration();
        hcc.getRetryHandler().setRetries(0);

        var proxy = getServiceProxy(3045, "localhost", 80);
        var ri = new RequestInterceptor();
        var si = new StaticInterceptor();
        si.setSrc("Dummy");
        ri.getFlow().add(si);
        ri.getFlow().add(new ReturnInterceptor());
        proxy.getFlow().add(ri);
        router = new DefaultRouter();
        router.add(proxy);
        router.start();
        setClientConfig(router, hcc);

        var proxy1 = getServiceProxy(3046, "localhost", 3045);
        proxy1.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                middleExchange.set(exc);
                return super.handleRequest(exc);
            }
        });
        router2 = new DefaultRouter();
        router2.setExchangeStore(lmes);
        router2.add(proxy1);
        router2.start();
        setClientConfig(router2, hcc);
    }

    private static void setClientConfig(Router router, HttpClientConfiguration hcc) {
        var client = getHttpClientInterceptor(router);
        client.updateHttpClientConfig(hcc);
    }

    private static @NotNull ServiceProxy getServiceProxy(int port, String targetHost, int targetPort) {
        return new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port), targetHost, targetPort);
    }

    private static @NotNull HTTPClientInterceptor getHttpClientInterceptor(Router router) {
        return router.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).orElseThrow();
    }

    @AfterEach
    void shutdown() {
        router.stop();
        router2.stop();
    }

    @Test
    void small() throws Exception {
        long len = 100;
        call(prepareExchange(len).buildExchange());
        assertEquals(1, lmes.getAllExchangesAsList().size());
        assertEquals(len, lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength());
    }

    @Test
    void large() throws Exception {
        long len = MAX_VALUE + 1L;
        call(prepareExchange(len).buildExchange());
        long snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

    @Test
    void largeChunked() throws Exception {
        long len = MAX_VALUE + 1L;
        Exchange e = prepareExchange(len).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        call(e);
        long snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

    private static Builder prepareExchange(long len) throws URISyntaxException {
        return post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len));
    }

    private void call(Exchange e) throws Exception {
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertEquals(200, e.getResponse().getStatusCode());
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
    }

}
