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

import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.test.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;
import java.nio.*;
import java.time.*;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

class HttpServerHandlerTest {

    private static final int PORT = 3077;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static Router router;

    @BeforeAll
    static void startTlsListener() throws Exception {
        router = new TestRouter();
        router.getConfiguration().setHotDeploy(false);
        final var sp = new ServiceProxy();
        sp.setPort(PORT);
        final var ks = new KeyStore();
        ks.setLocation("classpath:/alias-keystore.p12");
        ks.setKeyPassword("secret");
        ks.setKeyAlias("key1");
        final var ssl = new SSLParser();
        ssl.setKeyStore(ks);
        sp.setSslInboundParser(ssl);
        router.add(sp);
        router.start();
    }

    @AfterAll
    static void stopTlsListener() {
        router.stop();
    }

    private static final String HANDSHAKE_FAILED = "TLS handshake with client failed: ";

    /**
     * A client that sends its ClientHello and then hangs up makes the JDK report "Remote host terminated the
     * handshake". The log line must say that the handshake with the client failed, because the JDK message alone
     * reads as if the backend was the problem.
     */
    @Test
    void clientHangingUpDuringHandshakeIsLogged() throws Exception {
        final var log = startCapture();
        try {
            sendClientHelloAndHangUp();
            log.awaitContainsOrThrow(HANDSHAKE_FAILED + "Remote host terminated the handshake", TIMEOUT);
        } finally {
            stopCapture(log);
        }
    }

    /**
     * The client aborts the handshake because it does not trust the certificate. Depending on timing the server sees
     * the client's alert or a reset connection, before the fix the latter was only logged at debug.
     */
    @Test
    void clientRejectingTheCertificateIsLogged() throws Exception {
        final var log = startCapture();
        try {
            // The JDK default trust store does not trust the test certificate.
            try (final var socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket("localhost", PORT)) {
                assertThrows(SSLHandshakeException.class, socket::startHandshake);
            }
            log.awaitContainsOrThrow(HANDSHAKE_FAILED, TIMEOUT);
        } finally {
            stopCapture(log);
        }
    }

    /**
     * Closing an established connection without sending a request, e.g. a browser's unused preconnect, is not a
     * failed handshake.
     */
    @Test
    void closingAfterSuccessfulHandshakeIsNotLogged() throws Exception {
        final var log = startCapture();
        try {
            try (final var socket = (SSLSocket) trustingSocketFactory().createSocket("localhost", PORT)) {
                socket.startHandshake();
                socket.shutdownOutput();
                socket.getInputStream().readAllBytes();
            }
            // A failing connection afterwards, so the server has handled the first one by the time its line is logged.
            sendClientHelloAndHangUp();
            log.awaitContainsOrThrow(HANDSHAKE_FAILED + "Remote host terminated the handshake", TIMEOUT);
            assertEquals(1, log.eventsContaining(HANDSHAKE_FAILED).size(), log.getMessages().toString());
        } finally {
            stopCapture(log);
        }
    }

    /**
     * RFC 9112, section 6.3: the client ends a response to HEAD after its header fields, whatever they announce.
     * Anything sent after them is read as the start of the next response on the connection.
     */
    @Nested
    class HeadRequest {

        private static final int TIMEOUT_MILLIS = 5000;

        private final Router gateway = new TestRouter();

        @AfterEach
        void stopGateway() {
            gateway.stop();
        }

        @Test
        void bodySetInTheFlowIsNotSent() throws Exception {
            final var port = freePort();
            gateway.add(responding(port, "*", exc -> Response.ok("hello").build()));
            gateway.start();

            try (final var client = new Socket("localhost", port)) {
                client.setSoTimeout(TIMEOUT_MILLIS);
                final var in = new BufferedInputStream(client.getInputStream());

                // The length a GET gets
                assertEquals(5, sendHead(client, in).getHeader().getContentLength());
                assertNextResponseStartsWithStatusLine(client, in);
            }
        }

        /**
         * Plugins such as json2Soap send a request on as POST, but the client still sent HEAD.
         */
        @Test
        void methodChangedByTheFlowStillGetsNoBody() throws Exception {
            final var port = freePort();
            gateway.add(responding(port, "*", exc -> {
                exc.getRequest().setMethod("POST");
                return Response.ok("hello").build();
            }));
            gateway.start();

            try (final var client = new Socket("localhost", port)) {
                client.setSoTimeout(TIMEOUT_MILLIS);
                final var in = new BufferedInputStream(client.getInputStream());

                assertEquals(200, sendHead(client, in).getStatusCode());
                assertNextResponseStartsWithStatusLine(client, in);
            }
        }

        @Test
        void chunkedResponseFromBackendGetsNoLastChunk() throws Exception {
            try (final var backend = new ServerSocket(0);
                 final var executor = Executors.newSingleThreadExecutor()) {
                backend.setSoTimeout(TIMEOUT_MILLIS);
                final var backendTask = executor.submit(() -> {
                    try (final var peer = backend.accept()) {
                        new Request().read(peer.getInputStream(), true);
                        peer.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(US_ASCII));
                        peer.getOutputStream().flush();
                    }
                    return null;
                });
                final var port = freePort();
                gateway.add(new ServiceProxy(new ServiceProxyKey("*", "HEAD", ".*", port), "localhost", backend.getLocalPort()));
                gateway.add(responding(port, "GET", exc -> Response.ok("hello").build()));
                gateway.start();

                try (final var client = new Socket("localhost", port)) {
                    client.setSoTimeout(TIMEOUT_MILLIS);
                    final var in = new BufferedInputStream(client.getInputStream());

                    assertEquals(200, sendHead(client, in).getStatusCode());
                    backendTask.get(TIMEOUT_MILLIS, MILLISECONDS);
                    assertNextResponseStartsWithStatusLine(client, in);
                }
            }
        }

        private static ServiceProxy responding(final int port, final String method, final Function<Exchange, Response> response) {
            final var proxy = new ServiceProxy(new ServiceProxyKey("*", method, ".*", port), "", -1);
            proxy.getFlow().add(new AbstractInterceptor() {
                @Override
                public Outcome handleRequest(Exchange exc) {
                    exc.setResponse(response.apply(exc));
                    return RETURN;
                }
            });
            return proxy;
        }

        private static Response sendHead(final Socket client, final InputStream in) throws Exception {
            client.getOutputStream().write("HEAD / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII));
            final var response = new Response();
            response.read(in, false);
            return response;
        }

        /**
         * Sends a GET on the same connection and reads the first line of its response as is, because
         * <code>Response.read()</code> skips bytes in front of the status line.
         */
        private static void assertNextResponseStartsWithStatusLine(final Socket client, final InputStream in) throws Exception {
            client.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(US_ASCII));
            final var line = new ByteArrayOutputStream();
            for (var b = in.read(); b != '\n' && b != -1; b = in.read())
                line.write(b);
            final var statusLine = line.toString(US_ASCII);
            assertTrue(statusLine.startsWith("HTTP/1.1 200 "), statusLine);
        }
    }

    private static TestAppender startCapture() {
        final var appender = new TestAppender("tlsHandshake");
        appender.start();
        ((Logger) LogManager.getRootLogger()).addAppender(appender);
        return appender;
    }

    private static void stopCapture(TestAppender appender) {
        ((Logger) LogManager.getRootLogger()).removeAppender(appender);
        appender.stop();
    }

    private static SSLSocketFactory trustingSocketFactory() throws Exception {
        final var trustStore = java.security.KeyStore.getInstance("PKCS12");
        try (final var in = HttpServerHandlerTest.class.getResourceAsStream("/alias-truststore.p12")) {
            trustStore.load(in, "secret".toCharArray());
        }
        final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final var context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private static void sendClientHelloAndHangUp() throws Exception {
        final var engine = SSLContext.getDefault().createSSLEngine("localhost", PORT);
        engine.setUseClientMode(true);
        final var clientHello = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
        engine.wrap(ByteBuffer.allocate(0), clientHello);
        clientHello.flip();

        try (final var socket = new Socket("localhost", PORT)) {
            socket.getOutputStream().write(clientHello.array(), 0, clientHello.limit());
            // Half-close (FIN, not RST), then drain the server's flight until it closes the connection.
            socket.shutdownOutput();
            socket.getInputStream().readAllBytes();
        }
    }
}
