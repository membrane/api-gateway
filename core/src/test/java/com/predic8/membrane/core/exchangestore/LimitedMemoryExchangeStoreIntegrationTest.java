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

package com.predic8.membrane.core.exchangestore;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.*;

public class LimitedMemoryExchangeStoreIntegrationTest {
    private static LimitedMemoryExchangeStore lmes;
    private static HttpRouter router;
    private static HttpRouter router2;
    private static HttpClientConfiguration hcc;
    private static AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    @BeforeAll
    public static void setup() throws Exception {
        lmes = new LimitedMemoryExchangeStore();
        lmes.setMaxSize(500000);

        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.setMaxRetries(1);

        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3045), "thomas-bayer.com", 80);
        proxy.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok().body("").build());
                return Outcome.RETURN;
            }
        });
        router = new HttpRouter();

        ((HTTPClientInterceptor) router.getTransport().getInterceptors().get(3)).setHttpClientConfig(hcc);

        router.getRuleManager().addProxyAndOpenPortIfNew(proxy);
        router.init();

        ServiceProxy proxy1 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3046), "localhost", 3045);
        proxy1.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                middleExchange.set(exc);
                return super.handleRequest(exc);
            }
        });
        router2 = new HttpRouter();
        router2.setExchangeStore(lmes);

        ((HTTPClientInterceptor) router2.getTransport().getInterceptors().get(3)).setHttpClientConfig(hcc);

        router2.getTransport().getInterceptors().add(3, new ExchangeStoreInterceptor(lmes));

        router2.getRuleManager().addProxyAndOpenPortIfNew(proxy1);
        router2.init();
    }

    @BeforeEach
    public void init() {
        lmes.removeAllExchanges((AbstractExchange[]) lmes.getAllExchanges());
    }

    @AfterAll
    public static void shutdown() {
        if (router != null)
            router.shutdown();
        if (router2 != null)
            router2.shutdown();
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
        try(HttpClient hc = new HttpClient(hcc)) {
            hc.call(e);
        }
        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
        int snappedLength = lmes.getAllExchangesAsList().getFirst().getRequest().getBody().getLength();
        assertTrue(100000 <= snappedLength && snappedLength <= 150000);
    }

}
