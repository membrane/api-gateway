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

import com.google.common.base.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.google.common.base.Stopwatch.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class HttpTimeoutTest {

    public final int BACKEND_DELAY_MILLIS = 300;

    Router slowBackend, proxyRouter;

    @BeforeEach
    public void setUp() throws Exception {
        setupMembrane();
        setupSlowBackend();
    }

    @AfterEach
    public void tearDown() {
        slowBackend.stop();
        proxyRouter.stop();
    }

    private void setupMembrane() throws IOException {
        HttpClientConfiguration hcc = new HttpClientConfiguration();
        hcc.getConnection().setSoTimeout(1);
        hcc.getRetryHandler().setRetries(1);

        proxyRouter = new TestRouter();
        ServiceProxy sp2 = getServiceProxy(3023, "localhost", 3022);
        proxyRouter.add(sp2);
        proxyRouter.start();
        var client = proxyRouter.getTransport().getFirstInterceptorOfType(HTTPClientInterceptor.class).orElseThrow();
        client.setHttpClientConfig(hcc);
        client.init(); // Copies the config into the HttpClient. It is needed to call because router.start() above already called init()
    }

    private static @NotNull ServiceProxy getServiceProxy(int port, String localhost, int targetPort) {
        return new ServiceProxy(new ServiceProxyKey("*",
                "*", ".*", port), localhost, targetPort);
    }

    private void setupSlowBackend() throws Exception {
        slowBackend = new TestRouter();
        ServiceProxy sp = getServiceProxy(3022, "", -1);
        sp.getFlow().add(getHandler());
        slowBackend.add(sp);
        slowBackend.start();
    }

    private @NotNull AbstractInterceptor getHandler() {
        return new AbstractInterceptor() {
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
                exc.setResponse(ok("OK.").build());
                return RETURN;
            }
        };
    }

    @Test
    void httpTimeout() throws Exception {
        HttpClientConfiguration hcc = new HttpClientConfiguration();
        hcc.getRetryHandler().setRetries(1);

        Stopwatch watch = createStarted();

        try (HttpClient client = new HttpClient(hcc)) {
            Exchange exc = get("http://localhost:3023").buildExchange();
            client.call(exc);

            assertEquals(500, exc.getResponse().getStatusCode());
        }

        watch.stop();
        // since the timeout is at 100ms, the whole test should take <250ms
        assertTrue(watch.elapsed().getSeconds() < 5, "Test took " + watch.elapsed());
    }


}
