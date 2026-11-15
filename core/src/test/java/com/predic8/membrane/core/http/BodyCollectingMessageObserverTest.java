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

package com.predic8.membrane.core.http;

import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.http.BodyCollectingMessageObserver.Strategy.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyCollectingMessageObserverTest {

    private static final String CONTENT = "{\"name\":\"Ice cream\"}";

    @Test
    void readBody() {
        final var body = body();
        final var observer = observe(body);
        body.read();
        assertEquals(CONTENT, observer.collected);
    }

    @Test
    void streamedBody() {
        final var body = body();
        final var observer = observe(body);
        body.write(new PlainBodyTransferer(new ByteArrayOutputStream()), false);
        assertEquals(CONTENT, observer.collected);
    }

    /**
     * A body nobody read, e.g. of a request the gateway answered itself, is discarded at the end of
     * the exchange. The observer still gets the content.
     */
    @Test
    void discardedBody() {
        final var body = body();
        final var observer = observe(body);
        body.discard();
        assertEquals(CONTENT, observer.collected);
    }

    @Test
    void discardedChunkedBody() {
        final var body = new ChunkedBody(new ByteArrayInputStream("%x\r\n%s\r\n0\r\n\r\n".formatted(CONTENT.length(), CONTENT).getBytes(UTF_8)));
        final var observer = observe(body);
        body.discard();
        assertEquals(CONTENT, observer.collected);
    }

    /**
     * A body read part-way through its stream is discarded from where the reading stopped. The observer
     * gets the chunks read before and the ones dropped by the discard.
     */
    @Test
    void chunkedBodyDiscardedAfterPartialRead() throws IOException {
        final var first = CONTENT.substring(0, 8);
        final var second = CONTENT.substring(8);
        final var in = new ByteArrayInputStream("%x\r\n%s\r\n%x\r\n%s\r\n0\r\n\r\nNEXT".formatted(
                first.length(), first, second.length(), second).getBytes(UTF_8));
        final var body = new ChunkedBody(in);
        final var observer = observe(body);

        assertEquals(CONTENT.charAt(0), body.getContentAsStream().read());
        assertNull(observer.collected);
        body.discard();

        assertEquals(CONTENT, observer.collected);
        assertEquals("NEXT", new String(in.readAllBytes(), UTF_8), "the next message must stay in the stream");
    }

    /**
     * {@code limitedMemoryExchangeStore} evicts by the estimated heap size, which counts only read
     * bodies. The content collected from a discarded body is held in memory, so it must be counted.
     */
    @Test
    void snapshotOfDiscardedBodyCountsCollectedContent() {
        final var request = new Request();
        request.setBody(body());
        final Request snapshot = request.createSnapshot(() -> {}, ERROR, -1);
        final var sizeWithoutBody = snapshot.estimateHeapSize();
        request.getBody().discard();
        assertEquals(sizeWithoutBody + CONTENT.length(), snapshot.estimateHeapSize());
    }

    @Test
    void snapshotOfStreamedBodyCountsCollectedContent() {
        final var request = new Request();
        request.setBody(body());
        final Request snapshot = request.createSnapshot(() -> {}, ERROR, -1);
        final var sizeWithoutBody = snapshot.estimateHeapSize();
        request.getBody().write(new PlainBodyTransferer(new ByteArrayOutputStream()), false);
        assertEquals(sizeWithoutBody + CONTENT.length(), snapshot.estimateHeapSize());
    }

    /**
     * The body rebuilt from the collected chunks shares them instead of copying them, so a snapshot
     * holds its content in memory once, as counted by the estimated heap size.
     */
    @Test
    void collectedBodySharesCollectedChunks() {
        final var discarded = body();
        discarded.discard();
        final var observer = new CollectingObserver();
        final var chunk = new Chunk(CONTENT.getBytes(UTF_8));
        observer.bodyChunk(chunk);

        final var collected = observer.getBody(discarded);

        assertTrue(collected.isRead());
        assertSame(chunk, collected.chunks.getFirst());
        assertEquals(CONTENT, new String(collected.getContent(), UTF_8));
    }

    private static Body body() {
        return new Body(new ByteArrayInputStream(CONTENT.getBytes(UTF_8)), CONTENT.length());
    }

    private static CollectingObserver observe(AbstractBody body) {
        final var observer = new CollectingObserver();
        body.addObserver(observer);
        return observer;
    }

    private static class CollectingObserver extends BodyCollectingMessageObserver {

        String collected;

        CollectingObserver() {
            super(ERROR, -1);
        }

        @Override
        public void bodyRequested(AbstractBody body) {
        }

        @Override
        public void bodyComplete(AbstractBody body) {
            collected = new String(getBody(body).getContent(), UTF_8);
        }
    }
}
