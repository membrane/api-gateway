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

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.config.security.KeyStore;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyTest {

    static Router router;
    static final AtomicReference<String> lastMethod = new AtomicReference<>();

    @BeforeAll
    public static void init() throws IOException {

        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);

        ProxyRule rule = new ProxyRule(new ProxyRuleKey(3055));
        rule.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                lastMethod.set(exc.getRequest().getMethod());
                return super.handleRequest(exc);
            }
        });
        router.add(rule);

        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3056), null, 0);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("secret1").build());
                return RETURN;
            }
        });
        router.add(sp);

        SSLParser sslParser = new SSLParser();
        sslParser.setKeyStore(new KeyStore());
        sslParser.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        sslParser.getKeyStore().setKeyPassword("secret");
        ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey(3057), null, 0);
        sp2.setSslInboundParser(sslParser);
        sp2.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok("secret2").build());
                return RETURN;
            }
        });
        router.add(sp2);

        router.start();
    }

    @AfterAll
    public static void done() {
        router.stop();
    }

    @Test
    public void runHTTP() throws IOException {
        CloseableHttpClient hc = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet("http://localhost:3056");
        httpGet.setConfig(RequestConfig.custom()
                .setProxy(new HttpHost("localhost", 3055))
                .build());
        CloseableHttpResponse response = hc.execute(httpGet);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertTrue(EntityUtils.toString(response.getEntity()).contains("secret1"));
        assertEquals("GET", lastMethod.get());

        hc.close();
    }

    @Test
    public void runHTTPS() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException, InterruptedException {
        SSLSocketFactory sslsf = new SSLSocketFactory("TLS", null, null, null, null,
                new TrustAllStrategy(), new AllowAllHostnameVerifier());

        CloseableHttpClient hc = HttpClientBuilder.create()
                .setSSLSocketFactory(sslsf)
                .build();
        HttpGet httpGet = new HttpGet("https://localhost:3057");
        httpGet.setConfig(RequestConfig.custom()
                .setProxy(new HttpHost("localhost", 3055))
                .build());
        CloseableHttpResponse response = hc.execute(httpGet);

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertTrue(EntityUtils.toString(response.getEntity()).contains("secret2"));
        assertEquals("CONNECT", lastMethod.get());

        hc.close();
        Thread.sleep(100);

        assertEquals("0", router.getTransport().getOpenBackendConnections(3055));
        assertEquals("0", router.getTransport().getOpenBackendConnections(3057));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/a|b",
            "/search?q={x}",
            "/bad%zz",
    })
    void unparsableAbsoluteUriIsForwardedToBackend(String path) throws Exception {
        // A plain socket as backend, since a Membrane API would reject some of these targets itself
        final var requestLine = new CompletableFuture<String>();
        try (final var backend = new ServerSocket(0)) {
            Thread.startVirtualThread(() -> {
                try (final var connection = backend.accept()) {
                    requestLine.complete(new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII)).readLine());
                    connection.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".getBytes(StandardCharsets.US_ASCII));
                } catch (IOException e) {
                    requestLine.completeExceptionally(e);
                }
            });

            try (final var socket = new Socket("localhost", 3055)) {
                // Sent over a raw socket, since an HTTP client refuses to send a URI that does not parse
                final var host = "localhost:" + backend.getLocalPort();
                socket.getOutputStream().write("GET http://%s%s HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n".formatted(host, path, host).getBytes(StandardCharsets.US_ASCII));
                final var response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);

                assertTrue(response.startsWith("HTTP/1.1 200"), response);
            }
            assertEquals("GET %s HTTP/1.1".formatted(path), requestLine.get(5, TimeUnit.SECONDS));
        }
    }

}
