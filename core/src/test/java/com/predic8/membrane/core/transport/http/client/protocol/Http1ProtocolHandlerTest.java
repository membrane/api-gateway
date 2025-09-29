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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.transport.http.client.protocol.AbstractProtocolHandler.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
            assertEquals(CONNECT, exc.getProperty(UPGRADED_PROTOCOL, String.class));
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


}