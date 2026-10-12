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

package com.predic8.membrane.core.transport.http.streampump;

import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.interceptors.WebSocketLogInterceptor;
import com.predic8.membrane.core.transport.ws.interceptors.WebSocketLogInterceptor.Encoding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.io.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketStreamPumpTest {

    /**
     * Shutdown and hot reload end connections via {@link StreamPump.StreamPumpStats#closeAllStreamPumps()},
     * which only reaches registered pumps.
     */
    @Test
    void runningPumpIsRegisteredAndClosedWithAllStreamPumps() throws Exception {
        final var api = new APIProxy();
        api.setFlow(List.of(new WebSocketInterceptor()));
        final var stats = new StreamPump.StreamPumpStats();

        final var frame = new WebSocketFrame(true, false, false, false, 2, true, new byte[]{1, 2, 3, 4}, new byte[10]).toBytes();
        final var clientToServer = new WebSocketStreamPump(new OpenConnectionInputStream(frame), new ByteArrayOutputStream(), stats, "->", api, true, null);
        final var serverToClient = new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), stats, "<-", api, false, null);
        clientToServer.init(serverToClient);
        serverToClient.init(clientToServer);

        final var thread = new Thread(clientToServer);
        thread.start();
        try {
            awaitTrue(() -> clientToServer.getTransferredBytes() == frame.length);
            assertTrue(stats.getStreamPumps().contains(clientToServer));

            stats.closeAllStreamPumps();

            thread.join(10_000);
            assertFalse(thread.isAlive());
            assertFalse(stats.getStreamPumps().contains(clientToServer));
        } finally {
            thread.interrupt();
        }
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        final var deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline)
                fail("Condition not met within 10 seconds.");
            Thread.sleep(10);
        }
    }

    /**
     * Delivers its content, then blocks like an idle connection until it is closed.
     */
    private static class OpenConnectionInputStream extends InputStream {

        private final InputStream content;
        private final CountDownLatch closed = new CountDownLatch(1);

        OpenConnectionInputStream(byte[] content) {
            this.content = new ByteArrayInputStream(content);
        }

        @Override
        public int read() throws IOException {
            final var buffer = new byte[1];
            return read(buffer, 0, 1) == -1 ? -1 : buffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (content.available() > 0)
                return content.read(b, off, len);
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            throw new IOException("Socket closed");
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    /**
     * A close frame may come without a status code (RFC 6455 5.5.1), e.g. from a browser's
     * <code>ws.close()</code>. It has to reach the server unchanged, with and without <code>wsLog</code>.
     *
     * @param wsLogEncoding encoding of a <code>wsLog</code> in the frame flow, <code>null</code> for an empty flow
     */
    @ParameterizedTest
    @NullSource
    @EnumSource(Encoding.class)
    void closeFrameWithoutStatusCodeIsForwarded(Encoding wsLogEncoding) throws Exception {
        final var webSocket = new WebSocketInterceptor();
        if (wsLogEncoding != null) {
            final var wsLog = new WebSocketLogInterceptor();
            wsLog.setEncoding(wsLogEncoding);
            webSocket.setFlow(List.of(wsLog));
        }
        final var api = new APIProxy();
        api.setFlow(List.of(webSocket));

        final var close = new WebSocketFrame(true, false, false, false, 8, true, new byte[]{1, 2, 3, 4}, new byte[0]).toBytes();
        final var toServer = new ByteArrayOutputStream();

        final var clientToServer = new WebSocketStreamPump(new ByteArrayInputStream(close), toServer, null, "->", api, true, null);
        final var serverToClient = new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), null, "<-", api, false, null);
        clientToServer.init(serverToClient);
        serverToClient.init(clientToServer);

        clientToServer.run();

        assertArrayEquals(close, toServer.toByteArray());
    }

    @Test
    void oversizedClientFrameIsAnsweredWithCloseMessageTooBig() throws Exception {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setMaxFramePayloadSize(512);
        final var api = new APIProxy();
        api.setFlow(List.of(webSocket));

        final var oversized = new WebSocketFrame(true, false, false, false, 2, true, new byte[]{1, 2, 3, 4}, new byte[513]).toBytes();
        final var toServer = new ByteArrayOutputStream();
        final var toClient = new ByteArrayOutputStream();

        final var clientToServer = new WebSocketStreamPump(new ByteArrayInputStream(oversized), toServer, null, "->", api, true, null);
        final var serverToClient = new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), toClient, null, "<-", api, false, null);
        clientToServer.init(serverToClient);
        serverToClient.init(clientToServer);

        clientToServer.run();

        assertEquals(0, toServer.size());
        // FIN + opcode 8 (close), unmasked as it travels to the client, payload: status 1009
        assertArrayEquals(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xF1}, toClient.toByteArray());
    }
}
