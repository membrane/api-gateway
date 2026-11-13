/* Copyright 2026 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.annot.Constants.CRLF;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Transfer codings other than <code>chunked</code>, e.g. <code>Transfer-Encoding: gzip, chunked</code>
 * (RFC 9112 section 7). Unlike a Content-Encoding, a transfer coding belongs to the message, not to
 * the content, so the bytes inside it are not the content yet. Membrane does not decode such codings:
 * plugins would work on the coded bytes, and an HTTP/2 client, which has no Transfer-Encoding
 * (RFC 9113 section 8.2.2), would get them as content. So a request carrying one is answered with
 * 501 (RFC 9112 section 6.1), a backend response carrying one with 502 (#3789).
 */
class TransferCodingTest {

    private static final String CONTENT = "{\"message\":\"1,2,3 with a gzip transfer coding\"}";

    private Router router;
    private ServerSocket backend;
    private int frontendPort;
    private volatile boolean running;

    /** The raw bytes the backend answers with. */
    private volatile byte[] backendResponse;
    private final BlockingQueue<Request> backendRequests = new LinkedBlockingQueue<>();

    /** Bodies a plugin reads with getBodyAsStringDecoded(). */
    private final List<String> requestBodiesSeenByFlow = new CopyOnWriteArrayList<>();
    private final List<String> responseBodiesSeenByFlow = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        backendResponse = plainResponse();
        backend = new ServerSocket(0);
        running = true;
        final var thread = new Thread(() -> {
            while (running) {
                try (Socket socket = backend.accept()) {
                    final var request = new Request();
                    request.read(socket.getInputStream(), true);
                    request.getBody().read();
                    backendRequests.add(request);
                    socket.getOutputStream().write(backendResponse);
                    socket.getOutputStream().flush();
                } catch (Exception e) {
                    // ServerSocket closed on teardown -> leave the loop.
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        frontendPort = freePort();
        router = new TestRouter();
        router.add(serviceProxy(frontendPort));
        router.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        running = false;
        backend.close();
        router.stop();
    }

    @Test
    void requestWithGzipTransferCodingIsAnsweredWith501() throws Exception {
        final var response = send(gzipTransferCodedRequest());
        assertEquals(501, response.getStatusCode());
        assertTrue(response.getHeader().isConnectionClose());
        assertTrue(backendRequests.isEmpty());
        assertTrue(requestBodiesSeenByFlow.isEmpty());
    }

    @Test
    void responseWithGzipTransferCodingIsAnsweredWith502() throws Exception {
        backendResponse = gzipTransferCodedResponse();
        final var response = send(plainRequest());
        assertEquals(502, response.getStatusCode());
        assertTrue(responseBodiesSeenByFlow.isEmpty());
        // The backend would answer the same way again, so the GET is not retried.
        assertEquals(1, backendRequests.size());
    }

    /**
     * Guards the check against rejecting plain chunked framing.
     */
    @Test
    void chunkedRequestAndResponseReachTheirReceivers() throws Exception {
        backendResponse = chunkedResponse();
        final var response = send(chunkedRequest());
        assertEquals(200, response.getStatusCode());
        assertEquals(CONTENT, response.getBodyAsStringDecoded());
        assertEquals(CONTENT, backendRequests.poll(10, SECONDS).getBodyAsStringDecoded());
        assertEquals(List.of(CONTENT), requestBodiesSeenByFlow);
        assertEquals(List.of(CONTENT), responseBodiesSeenByFlow);
    }

    /**
     * HTTP/2 has no Transfer-Encoding (RFC 9113 section 8.2.2): forwarding the response would drop
     * the header and leave the client with gzip bytes it has no way to recognize.
     */
    @Test
    void http2ClientGets502ForAResponseWithGzipTransferCoding() throws Exception {
        backendResponse = gzipTransferCodedResponse();
        final var http2Port = freePort();
        final var http2Router = new TestRouter();
        final var proxy = serviceProxy(http2Port);
        proxy.setSslInboundParser(http2ServerSsl());
        http2Router.add(proxy);
        http2Router.start();
        try {
            final var exc = new Request.Builder().get("https://localhost:" + http2Port + "/").buildExchange();
            // Closed before the router stops, which otherwise waits for the open HTTP/2 connection
            try (final var client = new HttpClient(http2ClientConfiguration())) {
                client.call(exc);
                exc.getResponse().getBody().read();
            }
            assertEquals(502, exc.getResponse().getStatusCode());
        } finally {
            http2Router.stop();
        }
    }

    private ServiceProxy serviceProxy(int port) {
        final var proxy = new ServiceProxy(new ServiceProxyKey(port), "localhost", backend.getLocalPort());
        proxy.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                requestBodiesSeenByFlow.add(exc.getRequest().getBodyAsStringDecoded());
                return CONTINUE;
            }

            @Override
            public Outcome handleResponse(Exchange exc) {
                responseBodiesSeenByFlow.add(exc.getResponse().getBodyAsStringDecoded());
                return CONTINUE;
            }
        });
        return proxy;
    }

    private Response send(byte[] request) throws Exception {
        try (final var socket = new Socket("localhost", frontendPort)) {
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();
            final var response = new Response();
            response.read(socket.getInputStream(), true);
            response.getBody().read();
            return response;
        }
    }

    private static byte[] gzipTransferCodedRequest() throws IOException {
        return concat(("POST / HTTP/1.1" + CRLF +
                       "Host: localhost" + CRLF +
                       "Content-Type: application/json" + CRLF +
                       "Transfer-Encoding: gzip, chunked" + CRLF +
                       "Connection: close" + CRLF +
                       CRLF).getBytes(US_ASCII),
                chunked(gzip(CONTENT)));
    }

    private static byte[] chunkedRequest() {
        return concat(("POST / HTTP/1.1" + CRLF +
                       "Host: localhost" + CRLF +
                       "Content-Type: application/json" + CRLF +
                       "Transfer-Encoding: chunked" + CRLF +
                       "Connection: close" + CRLF +
                       CRLF).getBytes(US_ASCII),
                chunked(CONTENT.getBytes(UTF_8)));
    }

    private static byte[] plainRequest() {
        return ("GET / HTTP/1.1" + CRLF +
                "Host: localhost" + CRLF +
                "Connection: close" + CRLF +
                CRLF).getBytes(US_ASCII);
    }

    private static byte[] gzipTransferCodedResponse() throws IOException {
        return concat(("HTTP/1.1 200 OK" + CRLF +
                       "Content-Type: application/json" + CRLF +
                       "Transfer-Encoding: gzip, chunked" + CRLF +
                       "Connection: close" + CRLF +
                       CRLF).getBytes(US_ASCII),
                chunked(gzip(CONTENT)));
    }

    private static byte[] chunkedResponse() {
        return concat(("HTTP/1.1 200 OK" + CRLF +
                       "Content-Type: application/json" + CRLF +
                       "Transfer-Encoding: chunked" + CRLF +
                       "Connection: close" + CRLF +
                       CRLF).getBytes(US_ASCII),
                chunked(CONTENT.getBytes(UTF_8)));
    }

    private static byte[] plainResponse() {
        return ("HTTP/1.1 200 OK" + CRLF +
                "Content-Length: 0" + CRLF +
                "Connection: close" + CRLF +
                CRLF).getBytes(US_ASCII);
    }

    private static byte[] chunked(byte[] data) {
        return concat((Integer.toHexString(data.length) + CRLF).getBytes(US_ASCII), data,
                (CRLF + "0" + CRLF + CRLF).getBytes(US_ASCII));
    }

    private static byte[] gzip(String content) throws IOException {
        final var out = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(out)) {
            gzip.write(content.getBytes(UTF_8));
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        final var out = new ByteArrayOutputStream();
        for (final var part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static SSLParser http2ServerSsl() {
        final var ssl = new SSLParser();
        ssl.setUseExperimentalHttp2(true);
        ssl.setEndpointIdentificationAlgorithm("");
        ssl.setShowSSLExceptions(true);
        ssl.setKeyStore(new KeyStore());
        ssl.getKeyStore().setLocation("classpath:/ssl-rsa.keystore");
        ssl.getKeyStore().setKeyPassword("secret");
        return ssl;
    }

    private static HttpClientConfiguration http2ClientConfiguration() {
        final var ssl = new SSLParser();
        ssl.setEndpointIdentificationAlgorithm("");
        ssl.setShowSSLExceptions(true);
        ssl.setTrustStore(new TrustStore());
        ssl.getTrustStore().setLocation("classpath:/ssl-rsa-pub.keystore");
        ssl.getTrustStore().setPassword("secret");
        final var configuration = new HttpClientConfiguration();
        configuration.setUseExperimentalHttp2(true);
        configuration.setSslParser(ssl);
        configuration.setBaseLocation("/");
        return configuration;
    }
}
