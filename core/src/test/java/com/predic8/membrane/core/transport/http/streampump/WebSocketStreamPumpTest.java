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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import com.predic8.membrane.core.transport.ws.interceptors.WebSocketLogInterceptor;
import com.predic8.membrane.core.transport.ws.interceptors.WebSocketLogInterceptor.Encoding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static java.nio.charset.StandardCharsets.UTF_8;
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
        webSocket.setMaxMessageSize(512);
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

    /**
     * Frame interceptors see whole messages: fragments are joined, control frames in between pass right away.
     */
    @Test
    void fragmentedMessageIsCoalescedForFrameInterceptors() throws Exception {
        final var recorder = new RecordingInterceptor();
        final var start = frame(false, 1, new byte[]{1, 2, 3, 4}, "Hel");
        final var ping = frame(true, 9, new byte[]{5, 6, 7, 8}, "p");
        final var end = frame(true, 0, new byte[]{9, 10, 11, 12}, "lo");

        final var relayed = relayFromClient(apiWithFrameFlow(recorder, 1024), start, ping, end);

        final var message = frame(true, 1, new byte[]{1, 2, 3, 4}, "Hello");
        assertEquals(List.of(toList(ping), toList(message)), recorder.frames);
        assertArrayEquals(concat(ping, message), relayed.toServer());
    }

    /**
     * Frame interceptors like the STOMP reassembler read the handshake from the frame, so a joined message
     * must carry it just like an unfragmented one.
     */
    @Test
    void fragmentedMessageKeepsOriginalExchange() throws Exception {
        final var exchanges = new ArrayList<Exchange>();
        final var recorder = new RecordingInterceptor() {
            @Override
            public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
                exchanges.add(frame.getOriginalExchange());
                super.handleFrame(frame, frameTravelsToRight, sender);
            }
        };
        final var handshake = new Exchange(null);

        relayFromClient(apiWithFrameFlow(recorder, 1024), handshake,
                frame(true, 1, new byte[]{1, 2, 3, 4}, "Hi"),
                frame(false, 1, new byte[]{1, 2, 3, 4}, "Hel"),
                frame(true, 0, new byte[]{1, 2, 3, 4}, "lo"));

        assertEquals(2, exchanges.size());
        assertSame(handshake, exchanges.getFirst());
        assertSame(handshake, exchanges.get(1));
    }

    @Test
    void fragmentedMessageAboveMaxMessageSizeIsClosedWithMessageTooBig() throws Exception {
        final var recorder = new RecordingInterceptor();
        final var relayed = relayFromClient(apiWithFrameFlow(recorder, 8),
                frame(false, 2, new byte[]{1, 2, 3, 4}, "12345"),
                frame(true, 0, new byte[]{1, 2, 3, 4}, "67890"));

        assertTrue(recorder.frames.isEmpty());
        assertEquals(0, relayed.toServer().length);
        assertArrayEquals(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xF1}, relayed.toClient()); // 1009
    }

    @Test
    void continuationWithoutStartIsClosedWithProtocolError() throws Exception {
        final var recorder = new RecordingInterceptor();
        final var relayed = relayFromClient(apiWithFrameFlow(recorder, 1024), frame(true, 0, new byte[]{1, 2, 3, 4}, "lo"));

        assertTrue(recorder.frames.isEmpty());
        assertEquals(0, relayed.toServer().length);
        assertArrayEquals(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xEA}, relayed.toClient()); // 1002
    }

    @Test
    void closeDuringFragmentedMessageDropsThePartialMessage() throws Exception {
        final var recorder = new RecordingInterceptor();
        final var close = frame(true, 8, new byte[]{1, 2, 3, 4}, "");
        final var relayed = relayFromClient(apiWithFrameFlow(recorder, 1024), frame(false, 1, new byte[]{1, 2, 3, 4}, "Hel"), close);

        assertEquals(1, recorder.frames.size());
        assertArrayEquals(close, relayed.toServer());
    }

    /**
     * A failing frame interceptor must not lose a message silently: both peers get close 1011 (internal error).
     */
    @Test
    void failingFrameInterceptorClosesBothSidesWithInternalError() throws Exception {
        final var failing = new RecordingInterceptor() {
            @Override
            public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) {
                throw new IllegalStateException("broken");
            }
        };
        final var relayed = relayFromClient(apiWithFrameFlow(failing, 1024), frame(true, 1, new byte[]{1, 2, 3, 4}, "Hello"));

        assertArrayEquals(new byte[]{(byte) 0x88, 2, 0x03, (byte) 0xF3}, relayed.toClient()); // 1011, unmasked

        final var toServer = new ArrayList<byte[]>();
        new WebSocketFrameAssembler(new ByteArrayInputStream(relayed.toServer()), null).readFrames(f -> {
            assertTrue(f.isMasked());
            toServer.add(Arrays.copyOf(f.getPayload(), (int) f.getPayloadLength()));
            assertEquals(8, f.getOpcode());
        });
        assertEquals(1, toServer.size());
        assertArrayEquals(new byte[]{0x03, (byte) 0xF3}, toServer.getFirst());
    }

    private record Relayed(byte[] toServer, byte[] toClient) {}

    /**
     * Runs the client-to-server direction of a connection until the client's frames are used up.
     */
    private static Relayed relayFromClient(APIProxy api, byte[]... clientFrames) {
        return relayFromClient(api, null, clientFrames);
    }

    private static Relayed relayFromClient(APIProxy api, Exchange originalExchange, byte[]... clientFrames) {
        final var toServer = new ByteArrayOutputStream();
        final var toClient = new ByteArrayOutputStream();
        final var clientToServer = new WebSocketStreamPump(new ByteArrayInputStream(concat(clientFrames)), toServer, null, "->", api, true, originalExchange);
        final var serverToClient = new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), toClient, null, "<-", api, false, null);
        clientToServer.init(serverToClient);
        serverToClient.init(clientToServer);
        clientToServer.run();
        return new Relayed(toServer.toByteArray(), toClient.toByteArray());
    }

    private static APIProxy apiWithFrameFlow(WebSocketInterceptorInterface frameInterceptor, int maxMessageSize) {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setMaxMessageSize(maxMessageSize);
        webSocket.setFlow(List.of(frameInterceptor));
        final var api = new APIProxy();
        api.setFlow(List.of(webSocket));
        return api;
    }

    private static byte[] frame(boolean fin, int opcode, byte[] maskKey, String payload) throws IOException {
        return new WebSocketFrame(fin, false, false, false, opcode, true, maskKey, payload.getBytes(UTF_8)).toBytes();
    }

    private static byte[] concat(byte[]... parts) {
        final var out = new ByteArrayOutputStream();
        for (byte[] part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static List<Byte> toList(byte[] bytes) {
        final var list = new ArrayList<Byte>();
        for (byte b : bytes)
            list.add(b);
        return list;
    }

    /**
     * Records each frame as it is sent on, as wire bytes, and passes it on.
     */
    private static class RecordingInterceptor implements WebSocketInterceptorInterface {

        final List<List<Byte>> frames = new ArrayList<>();

        @Override
        public void init(Router router) {
        }

        @Override
        public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
            frames.add(toList(frame.toBytes()));
            sender.handleFrame(frame);
        }
    }
}
