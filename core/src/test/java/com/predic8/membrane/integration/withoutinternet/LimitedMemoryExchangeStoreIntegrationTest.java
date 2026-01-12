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

package com.predic8.membrane.integration.withoutinternet;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.integration.withinternet.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static java.lang.Integer.*;
import static org.junit.jupiter.api.Assertions.*;

public class LimitedMemoryExchangeStoreIntegrationTest {
    private static LimitedMemoryExchangeStore lmes;
    private static DefaultRouter router;
    private static DefaultRouter router2;
    private static HttpClientConfiguration hcc;
    private static final AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        lmes = new LimitedMemoryExchangeStore();
        lmes.setMaxSize(500_000);
        lmes.removeAllExchanges((AbstractExchange[]) lmes.getAllExchanges());
        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.getRetryHandler().setRetries(1);

        ServiceProxy proxy = getServiceProxy(3045, "dummy", 80);
        proxy.getFlow().add(new ReturnInterceptor());
        router = new DefaultRouter();
        router.add(proxy);
        router.start();
        setClientConfig(router, hcc);

        ServiceProxy proxy1 = getServiceProxy(3046, "localhost", 3045);
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
        var global = new GlobalInterceptor();
        global.getFlow().add(new ExchangeStoreInterceptor(lmes));
        router.getRegistry().register("global", global);
    }

    private static void setClientConfig(Router router, HttpClientConfiguration hcc) {
        var client = getHttpClientInterceptor(router);
        client.setHttpClientConfig(hcc);
        client.init();
    }

    private static @NotNull ServiceProxy getServiceProxy(int port, String localhost, int targetPort) {
        return new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port), localhost, targetPort);
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
        int snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

    @Test
    void largeChunked() throws Exception {
        long len = MAX_VALUE + 1L;
        Exchange e = prepareExchange(len).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        call(e);
        int snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

    private static Builder prepareExchange(long len) throws URISyntaxException {
        return post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len));
    }

    private static void call(Exchange e) throws Exception {
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
    }

}
