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

package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketStompReassemblerTest {

    /**
     * One reassembler instance serves every connection of an API. While frame A of one
     * connection is being serialized, frame B of another connection is processed completely.
     * A must still come out with its own content.
     */
    @Test
    void concurrentConnectionsDoNotShareSerializationBuffer() throws Exception {
        final var aWritingBody = new CountDownLatch(1);
        final var bDone = new CountDownLatch(1);

        final var reassembler = new WebSocketStompReassembler();
        reassembler.setFlow(List.of(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if ("/a".equals(exc.getRequest().getHeader().getFirstValue("destination")))
                    exc.getRequest().setBody(new Body(new BlockingInputStream("A", aWritingBody, bDone)));
                return CONTINUE;
            }
        }));

        final var forwardedA = new AtomicReference<WebSocketFrame>();
        final var connectionA = Executors.newSingleThreadExecutor();
        try {
            final var a = connectionA.submit(() -> {
                reassembler.handleFrame(stompFrame("/a", "A"), true, forwardedA::set);
                return null;
            });

            assertTrue(aWritingBody.await(10, SECONDS));
            reassembler.handleFrame(stompFrame("/b", "B"), true, frame -> {});
            bDone.countDown();

            a.get(10, SECONDS);
        } finally {
            connectionA.shutdownNow();
        }

        final var payload = new String(forwardedA.get().getPayload(), UTF_8);
        assertTrue(payload.startsWith("SEND\ndestination:/a\n"), payload);
        assertFalse(payload.contains("/b"), payload);
        assertTrue(payload.endsWith("\n\nA\0"), payload);
    }

    @Test
    void bodyWithoutContentLengthIsKept() throws Exception {
        assertForwardedUnchanged("SEND\ndestination:/a\n\nhello\0");
    }

    @Test
    void bodyWithContentLengthIsKept() throws Exception {
        assertForwardedUnchanged("SEND\ndestination:/a\ncontent-length:5\n\nhello\0");
    }

    private static void assertForwardedUnchanged(String stomp) throws Exception {
        final var forwarded = new AtomicReference<WebSocketFrame>();
        new WebSocketStompReassembler().handleFrame(
                new WebSocketFrame(true, false, false, false, 1, false, new byte[4], stomp.getBytes(UTF_8)),
                true, forwarded::set);
        assertEquals(stomp, new String(forwarded.get().getPayload(), UTF_8));
    }

    private static WebSocketFrame stompFrame(String destination, String body) {
        final var stomp = "SEND\ndestination:%s\n\n%s\0".formatted(destination, body);
        return new WebSocketFrame(true, false, false, false, 1, false, new byte[4], stomp.getBytes(UTF_8));
    }

    /**
     * Signals {@code reading} on the first read and then waits for {@code proceed}.
     */
    private static class BlockingInputStream extends InputStream {

        private final InputStream content;
        private final CountDownLatch reading;
        private final CountDownLatch proceed;
        private boolean first = true;

        BlockingInputStream(String content, CountDownLatch reading, CountDownLatch proceed) {
            this.content = new ByteArrayInputStream(content.getBytes(UTF_8));
            this.reading = reading;
            this.proceed = proceed;
        }

        @Override
        public int read() throws IOException {
            awaitOnFirstRead();
            return content.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            awaitOnFirstRead();
            return content.read(b, off, len);
        }

        private void awaitOnFirstRead() throws IOException {
            if (!first)
                return;
            first = false;
            reading.countDown();
            try {
                if (!proceed.await(10, SECONDS))
                    throw new IOException("Timed out waiting for the other connection.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
    }
}
