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

import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.security.TrustStore;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.protocol.Http2ProtocolHandler;
import com.predic8.membrane.core.util.URIFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.transport.http2.StreamState.CLOSED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Http2ClientServerTest {
    private volatile Response response;
    private volatile Consumer<Request> requestAsserter;
    private volatile AbstractHttpHandler handler;
    private HttpClient hc;
    private HttpClientConfiguration clientConfiguration;
    private Router router;
    private static final ConcurrentHashMap<String, Boolean> connectionHashes = new ConcurrentHashMap<>();

    @BeforeEach
    public void setup() throws IOException {
        connectionHashes.clear();
        SSLParser sslParser = getSslParser();

        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3049), "localhost", 80);
        sp.setSslInboundParser(sslParser);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                handler = exc.getHandler();
                connectionHashes.put("" + ((HttpServerHandler)exc.getHandler()).getSrcOut().hashCode(), true);
                if (requestAsserter != null)
                    requestAsserter.accept(exc.getRequest());
                exc.setResponse(response);
                return RETURN;
            }
        });
        router.add(sp);
        router.start();

        SSLParser sslParser2 = new SSLParser();
        sslParser2.setEndpointIdentificationAlgorithm("");
        sslParser2.setShowSSLExceptions(true);
        sslParser2.setTrustStore(new TrustStore());
        sslParser2.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
        sslParser2.getTrustStore().setPassword("secret");

        HttpClientConfiguration configuration = clientConfiguration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        configuration.setSslParser(sslParser2);
        configuration.setBaseLocation("/");
        ConnectionConfiguration connection = new ConnectionConfiguration();
        connection.setKeepAliveTimeout(100);
        configuration.setConnection(connection);
        hc = new HttpClient(configuration);
    }

    private static @NotNull SSLParser getSslParser() {
        SSLParser sslParser = new SSLParser();
        sslParser.setUseExperimentalHttp2(true);
        sslParser.setEndpointIdentificationAlgorithm("");
        sslParser.setShowSSLExceptions(true);
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");
        return sslParser;
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
            assertNotNull(e[i].getProperty(Http2ProtocolHandler.HTTP2_PROTOCOL));

            Response r = e[i].getResponse();
            assertEquals(200, r.getStatusCode());
            assertEquals("", r.getBodyAsStringDecoded());
        }

        assertEquals(1, connectionHashes.size());
    }

    /**
     * A body that is still unread cannot be sent twice unless it is retained, so a retried request
     * has to keep working over HTTP/2 just as it does over HTTP/1.
     */
    @Test
    public void retriedRequestWithStreamedBodyIsReplayed() throws Exception {
        clientConfiguration.getRetryHandler().setFailOverOn5XX(true);
        clientConfiguration.getRetryHandler().setRetries(1);
        clientConfiguration.getRetryHandler().setDelay(1);

        this.response = Response.badGateway("upstream down").build();
        CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();
        this.requestAsserter = req -> received.add(req.getBodyAsStringDecoded());

        byte[] payload = "hello".getBytes(UTF_8);
        Exchange e = new Request.Builder()
                .put("https://localhost:3049")
                .body(payload.length, new ByteArrayInputStream(payload))
                .buildExchange();
        hc.call(e);

        assertNotNull(e.getProperty(Http2ProtocolHandler.HTTP2_PROTOCOL));
        assertEquals(502, e.getResponse().getStatusCode());
        assertEquals(List.of("hello", "hello"), received, "both attempts must carry the body");
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

        assertNotNull(e.getProperty(Http2ProtocolHandler.HTTP2_PROTOCOL));

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

        assertNotNull(e.getProperty(Http2ProtocolHandler.HTTP2_PROTOCOL));

        return e.getResponse();
    }

}
