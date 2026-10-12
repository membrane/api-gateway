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
package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;
import com.predic8.membrane.core.transport.http.TwoWayStreaming;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static com.predic8.membrane.core.http.Header.SEC_WEBSOCKET_KEY;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.transport.ws.WebSocketConnection.computeKeyResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class WebSocketConnectionTest {

    @Test
    public void testKeyResponse() {
        assertEquals("vvtlPs9jLaZ5KqY6wzvtYznMEpQ=",
                computeKeyResponse("+2chusljI/LtPLXb4+gMZg=="));
    }

    /**
     * The thread sending to the client is the connection's HTTP handler thread: it must be released as soon as the
     * client is gone, not only at the next poll.
     */
    @Test
    void senderIsReleasedWhenTheClientDisconnects() throws Exception {
        final var client = new FakeClient(new byte[0], false);
        final var sender = connect(client, new RecordingConnection());

        sender.join(5_000);
        assertFalse(sender.isAlive());
    }

    @Test
    void pingIsAnsweredWithPong() throws Exception {
        final var client = new FakeClient(clientFrame(9, "abc".getBytes(UTF_8)), false);
        final var sender = connect(client, new RecordingConnection());

        sender.join(5_000);
        assertFalse(sender.isAlive());
        assertArrayEquals(new byte[]{(byte) 0x8A, 3, 'a', 'b', 'c'}, client.out.toByteArray()); // pong, unmasked
    }

    /**
     * RFC 6455 7.1.1: the server answers a close frame with a close frame and then closes the TCP connection.
     */
    @Test
    void closeIsAnsweredAndTheConnectionClosed() throws Exception {
        final var client = new FakeClient(clientFrame(8, new byte[]{0x03, (byte) 0xE8}), true);
        try {
            final var sender = connect(client, new RecordingConnection());

            sender.join(5_000);
            assertFalse(sender.isAlive());
            assertTrue(client.isClosed());
            assertArrayEquals(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xE8}, client.out.toByteArray()); // 1000, unmasked
        } finally {
            client.close();
        }
    }

    @Test
    void onlyDataFramesReachOnMessage() throws Exception {
        final var connection = new RecordingConnection();
        final var client = new FakeClient(concat(clientFrame(9, new byte[0]), clientFrame(1, "hi".getBytes(UTF_8))), false);
        final var sender = connect(client, connection);

        sender.join(5_000);
        assertEquals(List.of(1), connection.opcodes);
    }

    @Test
    void upgradeWithoutKeyIsRejected() throws Exception {
        final var connections = new WebSocketConnectionCollection();
        final var exc = Request.get("/ws")
                .header("Connection", "Upgrade")
                .header("Upgrade", "websocket")
                .buildExchange(new FakeClient(new byte[0], false));

        assertEquals(RETURN, new RecordingConnection().handle(exc, connections));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals(0, connections.getSize());
    }

    /**
     * Upgrades the client's connection and starts relaying on a daemon thread, which runs the sender.
     */
    private static Thread connect(FakeClient client, WebSocketConnection connection) throws Exception {
        final Exchange exc = Request.get("/ws")
                .header(SEC_WEBSOCKET_KEY, "0KhkDyGsK+qtDANJAp3lgQ==")
                .header("Connection", "Upgrade")
                .header("Upgrade", "websocket")
                .buildExchange(client);
        assertEquals(RETURN, connection.handle(exc, new WebSocketConnectionCollection()));
        assertEquals(101, exc.getResponse().getStatusCode());

        final var sender = new Thread(exc::setCompleted);
        sender.setDaemon(true);
        sender.start();
        return sender;
    }

    private static byte[] clientFrame(int opcode, byte[] payload) throws IOException {
        return new WebSocketFrame(true, false, false, false, opcode, true, new byte[]{1, 2, 3, 4}, payload).toBytes();
    }

    private static byte[] concat(byte[]... parts) {
        final var out = new ByteArrayOutputStream();
        for (byte[] part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static class RecordingConnection extends WebSocketConnection {

        final List<Integer> opcodes = new CopyOnWriteArrayList<>();

        @Override
        public void onMessage(WebSocketFrame frame) {
            opcodes.add(frame.getOpcode());
        }
    }

    /**
     * A client connection: sends its content, then either ends (EOF) or stays open until closed.
     */
    private static class FakeClient extends FakeHttpHandler implements TwoWayStreaming {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final InputStream in;

        FakeClient(byte[] content, boolean staysOpen) {
            super(0);
            final var data = new ByteArrayInputStream(content);
            in = new InputStream() {
                @Override
                public int read() throws IOException {
                    final var b = new byte[1];
                    return read(b, 0, 1) == -1 ? -1 : b[0] & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (data.available() > 0)
                        return data.read(b, off, len);
                    if (staysOpen) {
                        try {
                            closed.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        }
                    }
                    return -1;
                }
            };
        }

        @Override
        public InputStream getSrcIn() {
            return in;
        }

        @Override
        public OutputStream getSrcOut() {
            return out;
        }

        @Override
        public String getRemoteDescription() {
            return "test";
        }

        @Override
        public void removeSocketSoTimeout() {
        }

        @Override
        public boolean isClosed() {
            return closed.getCount() == 0;
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }
}
