/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.function.*;

import static com.predic8.membrane.core.transport.http.HttpClient.*;
import static com.predic8.membrane.core.transport.http2.StreamState.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class Http2ClientServerTest {
    private volatile Response response;
    private volatile Consumer<Request> requestAsserter;
    private volatile AbstractHttpHandler handler;
    private HttpClient hc;
    private HttpRouter router;
    private static ConcurrentHashMap<String, Boolean> connectionHashes = new ConcurrentHashMap<>();

    @BeforeEach
    public void setup() {
        connectionHashes.clear();
        SSLParser sslParser = new SSLParser();
        sslParser.setUseExperimentalHttp2(true);
        sslParser.setEndpointIdentificationAlgorithm("");
        sslParser.setShowSSLExceptions(true);
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");

        router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3049), "localhost", 80);
        sp.setSslInboundParser(sslParser);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                handler = exc.getHandler();
                connectionHashes.put("" + ((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode(), true);
                if (requestAsserter != null)
                    requestAsserter.accept(exc.getRequest());
                exc.setResponse(response);
                return Outcome.RETURN;
            }
        });
        router.getRules().add(sp);
        router.start();


        SSLParser sslParser2 = new SSLParser();
        sslParser2.setEndpointIdentificationAlgorithm("");
        sslParser2.setShowSSLExceptions(true);
        sslParser2.setTrustStore(new TrustStore());
        sslParser2.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
        sslParser2.getTrustStore().setPassword("secret");

        HttpClientConfiguration configuration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        configuration.setSslParser(sslParser2);
        configuration.setBaseLocation("/");
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setKeepAliveTimeout(100);
        configuration.setConnection(connection);
        hc = new HttpClient(configuration);
    }

    @AfterEach
    public void done() {
        hc.close();
        router.stop();
    }

    @Test
    public void simple() throws Exception {
        test200("here");
    }


    @Test
    public void emptyBody() throws Exception {
        test200("");
    }

    @Test
    public void longBody() throws Exception {
        // 160k exceeds the max frame size as well as the initial window size
        test200("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+!@#$%^&*(){}?+S_|".repeat(2000)
                // 160k exceeds the max frame size as well as the initial window size
        );
    }

    @Test
    public void testQuery() throws Exception {
        Response r = test("GET", "/abc?def=ghi&jkl=mno", null, Response.ok().build());
        assertEquals(200, r.getStatusCode());
        assertEquals("", r.getBodyAsStringDecoded());
    }


    @Test
    public void testStreamInfoProperlyClosed() throws Exception {
        test200("");

        StreamInfo si = ((HttpServerHandler) handler).getHttp2ServerHandler().logic.streams.entrySet().stream().findFirst().get().getValue();
        assertEquals(CLOSED, si.getState());
    }

    @Test
    public void testParallelStreams() throws Exception {
        CountDownLatch cdl = new CountDownLatch(2);
        CountDownLatch cdl1 = new CountDownLatch(1);
        this.response = Response.ok().build();
        this.requestAsserter = req -> {
            cdl1.countDown();
            cdl.countDown();
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };

        Exchange[] e;
        try (ExecutorService es = Executors.newFixedThreadPool(2)) {
            e = new Exchange[2];
            for (int i = 0; i < 2; i++) {
                if (i == 1)
                    cdl1.await();
                e[i] = new Request.Builder().get("https://localhost:3049").buildExchange();
                int j = i;
                es.submit(() -> {
                    Thread.currentThread().setName("Requestor " + j);
                    try {
                        hc.call(e[j]);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }

            es.shutdown();
            es.awaitTermination(20, SECONDS);
        }

        for (int i = 0; i < 2; i++) {
            assertNotNull(e[i].getProperty(HTTP2));

            Response r = e[i].getResponse();
            assertEquals(200, r.getStatusCode());
            assertEquals("", r.getBodyAsStringDecoded());
        }

        assertEquals(1, connectionHashes.size());
    }

    private void test200(String body) throws Exception {
        Response r = testGet(Response.ok(body).build());

        assertEquals(200, r.getStatusCode());
        assertEquals(body, r.getBodyAsStringDecoded());
    }

    private Response testGet(Response response) throws Exception {
        this.response = response;
        this.requestAsserter = null;

        Exchange e = new Request.Builder().get("https://localhost:3049").buildExchange();
        hc.call(e);

        assertNotNull(e.getProperty(HTTP2));

        return e.getResponse();
    }

    private Response test(String method, String path, String body, Response response) throws Exception {
        this.response = response;
        this.requestAsserter = req -> {
            try {
                assertEquals(method, req.getMethod());
                assertEquals(path, req.getUri());
                if (body != null)
                    assertEquals(body, req.getBodyAsStringDecoded());
                // TODO: if body is null, assert empty request body
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        };

        Request.Builder b = new Request.Builder().url(new URIFactory(), "https://localhost:3049" + path).method(method);
        if (body != null) {
            b.body(body);
        }
        Exchange e = b.buildExchange();
        hc.call(e);

        assertNotNull(e.getProperty(HTTP2));

        return e.getResponse();
    }

}
