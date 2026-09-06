/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.ConnectionFactory.OutgoingConnectionType;
import com.predic8.membrane.core.transport.http.HostColonPort;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static com.predic8.membrane.core.http.Header.EXPECT;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.continue100;
import static com.predic8.membrane.core.transport.http.client.protocol.AbstractProtocolHandler.UPGRADED_PROTOCOL;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1ProtocolHandlerTest {

    Http1ProtocolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new Http1ProtocolHandler(new HttpClientConfiguration(), null);
    }

    @Nested
    class continueRequests {

        @Test
        void connectRequest() throws Exception {
            Exchange exc = connect("/foo").buildExchange();
            handler.handle(exc, null, new HostColonPort("localhost", 8080));

            Response res = exc.getResponse();
            assertEquals(200, res.getStatusCode());
            assertEquals(METHOD_CONNECT, exc.getProperty(UPGRADED_PROTOCOL, String.class));
        }

        @Test
        void connectThroughProxyAccepted() throws Exception {
            Exchange exc = sendConnectThroughProxy("HTTP/1.1 200 Connection established\r\n\r\n");

            assertEquals(200, exc.getResponse().getStatusCode());
            assertEquals(METHOD_CONNECT, exc.getProperty(UPGRADED_PROTOCOL, String.class));
        }

        /**
         * A proxy refusing the tunnel must not be reported to the client as a working tunnel.
         */
        @Test
        void connectThroughProxyRejected() throws Exception {
            Exchange exc = sendConnectThroughProxy("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n");

            assertEquals(502, exc.getResponse().getStatusCode());
            assertNull(exc.getProperty(UPGRADED_PROTOCOL, String.class), "no tunnel was established");
        }

        private static Exchange sendConnectThroughProxy(String proxyResponse) throws Exception {
            HttpClientConfiguration configuration = new HttpClientConfiguration();
            configuration.setProxy(new ProxyConfiguration());

            Exchange exc = connect("/foo").buildExchange();
            new Http1ProtocolHandler(configuration, null).handle(exc,
                    getConnectionType(getInputStreamFor(proxyResponse), new CollectingOutputStream()),
                    new HostColonPort("localhost", 8080));
            return exc;
        }
    }

    @Nested
    class complete100Continue {

        @Test
        void expectContinueHandshake() throws Exception {
            Exchange exc = post("/foo")
                    .header(EXPECT, "100-Continue")
                    .body("hello")
                    .buildExchange();

            // outbound stream that records everything the client writes
            CollectingOutputStream wire = new CollectingOutputStream();

            // make the first reader call return the interim 100‑Continue
            handler.responseReader = (e, ct) -> continue100().build();

            // Prepare to answer the 100-Continue
            handler.handle(exc, getConnectionType(
                    getInputStreamFor("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"), wire),
                    new HostColonPort("localhost", 8080));

            assertEquals(200, exc.getResponse().getStatusCode(), "Final 200 OK");

            // Check what the client has written
            String sent = new String(wire.toByteArray(), ISO_8859_1);
            assertTrue(sent.contains("POST /foo HTTP/1.1"), "request line must be written");
            assertTrue(sent.contains("Expect: 100-Continue"), "headers must be written");
            assertTrue(sent.contains("hello"), "body must be streamed after 100‑Continue");
        }

    }

    @Nested
    class RetryBodyRetention {

        private static final String RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";

        /**
         * With retries=1 the RetryHandler still performs a second attempt, so the request body has to
         * be retained for the replay.
         */
        @Test
        void bodyIsReplayedWhenOneRetryIsConfigured() throws Exception {
            Http1ProtocolHandler handler = handlerWithRetries(1);
            Exchange exc = streamedPostExchange("hello");

            assertTrue(sendOnFreshConnection(handler, exc).contains("hello"), "body on first attempt");
            assertTrue(sendOnFreshConnection(handler, exc).contains("hello"), "body on the retry");
        }

        @Test
        void bodyIsReplayedWithDefaultRetries() throws Exception {
            Http1ProtocolHandler handler = handlerWithRetries(2);
            Exchange exc = streamedPostExchange("hello");

            assertTrue(sendOnFreshConnection(handler, exc).contains("hello"), "body on first attempt");
            assertTrue(sendOnFreshConnection(handler, exc).contains("hello"), "body on the retry");
        }

        private static Http1ProtocolHandler handlerWithRetries(int retries) {
            HttpClientConfiguration configuration = new HttpClientConfiguration();
            configuration.getRetryHandler().setRetries(retries);
            return new Http1ProtocolHandler(configuration, null);
        }

        /**
         * A body that is still unread and backed by a stream - the case where the retainBody flag decides
         * whether the body survives the first write.
         */
        private static Exchange streamedPostExchange(String body) throws Exception {
            byte[] payload = body.getBytes(ISO_8859_1);
            return post("/foo").body(payload.length, new ByteArrayInputStream(payload)).buildExchange();
        }

        /**
         * Runs one attempt against a fresh connection and returns everything written to the wire.
         */
        private static String sendOnFreshConnection(Http1ProtocolHandler handler, Exchange exc) throws Exception {
            CollectingOutputStream wire = new CollectingOutputStream();
            handler.handle(exc, getConnectionType(getInputStreamFor(RESPONSE), wire), new HostColonPort("localhost", 8080));
            return new String(wire.toByteArray(), ISO_8859_1);
        }
    }

    private static @NotNull ByteArrayInputStream getInputStreamFor(String s) {
        return new ByteArrayInputStream(s.getBytes(ISO_8859_1));
    }

    private static @NotNull OutgoingConnectionType getConnectionType(InputStream respIn, CollectingOutputStream wire) throws Exception {
        return new OutgoingConnectionType(getConnectionMock(respIn, wire), false, null, null, "");
    }

    private static @NotNull Socket getSocketMock(InputStream respIn, CollectingOutputStream wire) throws Exception {
        Socket sock = mock(Socket.class);
        when(sock.getInputStream()).thenReturn(respIn);
        when(sock.getOutputStream()).thenReturn(wire);
        return sock;
    }

    private static @NotNull Connection getConnectionMock(InputStream respIn, CollectingOutputStream wire) throws Exception {
        Connection con = mock(Connection.class);
        con.in = respIn;
        con.out = wire;
        con.socket = getSocketMock(respIn, wire);
        return con;
    }

    /**
     * Collects every byte the handler writes; close() is a harmless no‑op. (JDK 21‑safe)
     */
    private static final class CollectingOutputStream extends OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            buf.write(b);
        }

        @Override
        public void write(byte[] b, int o, int l) {
            buf.write(b, o, l);
        }

        @Override
        public void write(byte[] b) {
            buf.write(b, 0, b.length);
        }

        @Override
        public void flush() { /* ignore */ }

        @Override
        public void close() { /* ignore */ }

        byte[] toByteArray() {
            return buf.toByteArray();
        }
    }


}
