/* Copyright 2014 predic8 GmbH, www.predic8.com

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

import com.google.common.base.Stopwatch;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.HTTPClientInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpTimeoutTest {
    public final int TIMEOUT_MILLIS = 100;
    public final int ASSERT_COMPLETED_AFTER_MILLIS = 250;
    public final int BACKEND_DELAY_MILLIS = 300;

    HttpRouter slowBackend, proxyRouter;

    @BeforeEach
    public void setUp() throws Exception {
        setupMembrane();
        setupSlowBackend();
    }

    private void setupMembrane() throws Exception {
        HttpClientConfiguration hcc = new HttpClientConfiguration();
        hcc.getConnection().setSoTimeout(TIMEOUT_MILLIS);
        hcc.setMaxRetries(1);

        proxyRouter = new HttpRouter();
        proxyRouter.setHotDeploy(false);
        proxyRouter.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).get().setHttpClientConfig(hcc);
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*",
                "*", ".*", 3023), "localhost", 3022);
        proxyRouter.getRules().add(sp2);
        proxyRouter.start();
    }

    private void setupSlowBackend() throws Exception {
        slowBackend = new HttpRouter();
        slowBackend.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*",
                "*", ".*", 3022), "", -1);
        sp.getInterceptors().add(new AbstractInterceptor(){
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    exc.getRequest().readBody();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    Thread.sleep(BACKEND_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                exc.setResponse(Response.ok("OK.").build());
                return Outcome.RETURN;
            }
        });
        slowBackend.getRuleManager().addProxyAndOpenPortIfNew(sp);
        slowBackend.init();
    }

    @AfterEach
    public void tearDown() {
        slowBackend.stop();
        proxyRouter.stop();
    }

    @Test
    public void testHttpTimeout() throws Exception {
        HttpClientConfiguration hcc = new HttpClientConfiguration();
        hcc.setMaxRetries(1);

        Stopwatch watch = Stopwatch.createStarted();

        try (HttpClient client = new HttpClient(hcc)) {
            var exc = client.call(new Request.Builder().get("http://localhost:3023").buildExchange());

            assertEquals(500, exc.getResponse().getStatusCode());
            System.out.println(exc.getResponse());
            System.out.println(exc.getResponse().getBodyAsStringDecoded());
        }

        watch.stop();
        // since the timeout is at 100ms, the whole test should take <250ms
        assertEquals(0, watch.elapsed().getSeconds(), "Test took " + watch.elapsed());
        assertTrue(watch.elapsed().getNano() < ASSERT_COMPLETED_AFTER_MILLIS * 1000000, "Test took " + watch.elapsed());
    }


}
