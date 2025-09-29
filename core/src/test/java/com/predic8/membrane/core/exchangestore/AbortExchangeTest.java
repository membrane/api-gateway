/* Copyright 2015 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.BodyUtil;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class AbortExchangeTest {

    Router router;

    @BeforeEach
    public void setup() throws Exception {
        router = new HttpRouter();

        LimitedMemoryExchangeStore es = new LimitedMemoryExchangeStore();
        router.setExchangeStore(es);
        router.getTransport().getFlow().add(2, new ExchangeStoreInterceptor(es));

        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 3031), "", -1);
        sp2.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    exc.getRequest().readBody();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                exc.setResponse(Response.ok("").body(new InputStream() {
                    int l = 0;

                    @Override
                    public int read() {
                        if (l++ >= 2000000)
                            return -1;
                        return 0;
                    }
                }, true).build());
                return RETURN;
            }
        });
        router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
        router.init();
    }

    @Test
    public void doit() throws Exception {
        Response response = performRequest();

        //noinspection ResultOfMethodCallIgnored
        response.getBodyAsStream().read(new byte[4096]);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);

        IOUtils.copy(response.getBodyAsStream(), new ByteArrayOutputStream());
        sleep(100);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 1);
    }

    @Test
    void abort() throws Exception {
        Response response = performRequest();
        //noinspection ResultOfMethodCallIgnored
        response.getBodyAsStream().read(new byte[4096]);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);

        BodyUtil.closeConnection(response.getBody());
        sleep(100);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);
    }

    private void assertExchangeStoreHas(ExchangeStore exchangeStore, int numberOfExchanges, int responsePresent) throws IOException {
        List<AbstractExchange> list = exchangeStore.getAllExchangesAsList();
        assertEquals(numberOfExchanges, list.size());
        for (AbstractExchange e : list) {
            assertTrue(responsePresent == 0 ? e.getResponse().getBody().getLength() == 0 : list.getFirst().getResponse().getBody().getLength() >= 1, "Exchange has " + (responsePresent == 1 ? "no " : "") + "response");
        }

    }

    private Response performRequest() throws Exception {
        try (HttpClient hc = new HttpClient()) {
            return hc.call(new Request.Builder().get("http://localhost:3031/").header("Connection", "close")
                    .buildExchange()).getResponse();
        }
    }

    @AfterEach
    public void done() {
        router.shutdown();
    }
}
