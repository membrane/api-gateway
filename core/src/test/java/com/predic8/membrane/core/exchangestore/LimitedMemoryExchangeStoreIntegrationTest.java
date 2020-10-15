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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.LargeBodyTest;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.http.Header.CHUNKED;
import static com.predic8.membrane.core.http.Header.TRANSFER_ENCODING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LimitedMemoryExchangeStoreIntegrationTest {
    private LimitedMemoryExchangeStore lmes;
    private HttpRouter router, router2;
    private HttpClientConfiguration hcc;
    private AtomicReference<Exchange> middleExchange = new AtomicReference<>();

    public void setup() throws Exception {
        lmes = new LimitedMemoryExchangeStore();
        lmes.setMaxSize(500000);

        // streaming only works for maxRetries = 1
        hcc = new HttpClientConfiguration();
        hcc.setMaxRetries(1);

        Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3045), "thomas-bayer.com", 80);
        rule.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.setResponse(Response.ok().body("").build());
                return Outcome.RETURN;
            }
        });
        router = new HttpRouter();

        ((HTTPClientInterceptor) router.getTransport().getInterceptors().get(3)).setHttpClientConfig(hcc);

        router.getRuleManager().addProxyAndOpenPortIfNew(rule);
        router.init();

        Rule rule1 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3046), "localhost", 3045);
        rule1.getInterceptors().add(new AbstractInterceptor() {
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

        router2.getRuleManager().addProxyAndOpenPortIfNew(rule1);
        router2.init();
    }

    @After
    public void shutdown() throws IOException {
        if (router != null)
            router.shutdown();
        if (router2 != null)
            router2.shutdown();
    }

    @Test
    public void small() throws Exception {
        setup();
        long len = 100;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).buildExchange();
        new HttpClient(hcc).call(e);

        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());

        assertEquals(1, lmes.getAllExchangesAsList().size());
        assertEquals(len, lmes.getAllExchangesAsList().get(0).getRequest().getBody().getLength());
    }

    @Test
    public void large() throws Exception {
        setup();
        long len = Integer.MAX_VALUE + 1l;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).buildExchange();
        new HttpClient(hcc).call(e);

        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
        int snappedLength = lmes.getAllExchangesAsList().get(0).getRequest().getBody().getLength();
        assertTrue( 100000 <= snappedLength && snappedLength <= 150000);
    }

    @Test
    public void largeChunked() throws Exception {
        setup();
        long len = Integer.MAX_VALUE + 1l;

        Exchange e = new Request.Builder().post("http://localhost:3046/foo").body(len, new LargeBodyTest.ConstantInputStream(len)).header(TRANSFER_ENCODING, CHUNKED).buildExchange();
        new HttpClient(hcc).call(e);

        assertTrue(e.getRequest().getBody().wasStreamed());
        assertTrue(middleExchange.get().getRequest().getBody().wasStreamed());
        int snappedLength = lmes.getAllExchangesAsList().get(0).getRequest().getBody().getLength();
        assertTrue( 100000 <= snappedLength && snappedLength <= 150000);
    }

}
