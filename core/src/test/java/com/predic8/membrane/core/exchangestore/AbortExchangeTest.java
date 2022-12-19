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

import com.predic8.io.IOUtil;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.BodyUtil;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.ConnectionManager;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientUtil;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.util.URIFactory;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AbortExchangeTest {

    Router router;

    @BeforeEach
    public void setup() throws Exception {
        router = new HttpRouter();

        LimitedMemoryExchangeStore es = new LimitedMemoryExchangeStore();
        router.setExchangeStore(es);
        router.getTransport().getInterceptors().add(2, new ExchangeStoreInterceptor(es));

        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 3031), "", -1);
        sp2.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                exc.getRequest().readBody();
                exc.setResponse(Response.ok("").body(new InputStream() {
                    int l = 0;

                    @Override
                    public int read() throws IOException {
                        if (l++ >= 2000000)
                            return -1;
                        return 0;
                    }
                }, true).build());
                return Outcome.RETURN;
            }
        });
        router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
        router.init();
    }

    @Test
    public void doit() throws Exception {
        Response response = performRequest();
        response.getBodyAsStream().read(new byte[4096]);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);

        IOUtils.copy(response.getBodyAsStream(), new ByteArrayOutputStream());
        Thread.sleep(100);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 1);
    }

    @Test
    public void abort() throws Exception {
        Response response = performRequest();
        response.getBodyAsStream().read(new byte[4096]);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);

        BodyUtil.closeConnection(response.getBody());
        Thread.sleep(100);

        assertExchangeStoreHas(router.getExchangeStore(), 1, 0);
    }

    private void assertExchangeStoreHas(ExchangeStore exchangeStore, int numberOfExchanges, int responsePresent) throws IOException {
        List<AbstractExchange> list = exchangeStore.getAllExchangesAsList();
        assertEquals(numberOfExchanges, list.size());
        for (AbstractExchange e : list) {
            assertTrue(responsePresent == 0 ? e.getResponse().getBody().getLength() == 0 : list.get(0).getResponse().getBody().getLength() >= 1, "Exchange has " + (responsePresent == 1 ? "no " : "") + "response");
        }

    }

    private Response performRequest() throws Exception {
        return new HttpClient().call(new Request.Builder().get("http://localhost:3031/").header("Connection", "close").buildExchange()).getResponse();
    }

    @AfterEach
    public void done() throws IOException {
        router.shutdown();
    }
}
