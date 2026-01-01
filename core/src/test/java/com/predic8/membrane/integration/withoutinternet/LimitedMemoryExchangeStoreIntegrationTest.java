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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.integration.withinternet.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class LimitedMemoryExchangeStoreIntegrationTest {
    private static LimitedMemoryExchangeStore lmes;
    private static Router router;
    private static Router router2;
    private static HttpClientConfiguration hcc;
    private static final AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    @BeforeAll
    static void setup() throws Exception {
        lmes = new LimitedMemoryExchangeStore();
        lmes.setMaxSize(500000);

        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.getRetryHandler().setRetries(1);

        ServiceProxy proxy = getServiceProxy(3045, "dummy", 80);
        proxy.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok().body("").build());
                return RETURN;
            }
        });
        router = new TestRouter();
        router.add(proxy);
        router.start();
        setClientConfig(router,hcc);

        ServiceProxy proxy1 = getServiceProxy(3046, "localhost", 3045);
        proxy1.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                middleExchange.set(exc);
                return super.handleRequest(exc);
            }
        });
        router2 = new TestRouter();
        router2.setExchangeStore(lmes);
        router2.add(proxy1);
        router2.start();
        setClientConfig(router2,hcc);
        router2.getTransport().getFlow().add(3, new ExchangeStoreInterceptor(lmes));
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

    @BeforeEach
    public void init() {
        lmes.removeAllExchanges((AbstractExchange[]) lmes.getAllExchanges());
    }

    @AfterAll
    public static void shutdown() {
        if (router != null)
            router.stop();
        if (router2 != null)
            router2.stop();
    }

    @Test
    public void small() throws Exception {
        long len = 100;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).buildExchange();
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());

        assertEquals(1, lmes.getAllExchangesAsList().size());
        assertEquals(len, lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength());
    }

    @Test
    public void large() throws Exception {
        long len = Integer.MAX_VALUE + 1L;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).buildExchange();
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
        int snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

    @Test
    public void largeChunked() throws Exception {
        long len = Integer.MAX_VALUE + 1L;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        try (HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
        int snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

}
