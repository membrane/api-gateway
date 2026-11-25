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

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import static com.predic8.membrane.core.http.BodyCollectingMessageObserver.Strategy.ERROR;
import static com.predic8.membrane.core.transport.http2.frame.DataFrame.FLAG_END_STREAM;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_DATA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class StreamInfoTest {

    // null sender is safe: the stream never reaches the CLOSED state
    private final StreamInfo streamInfo = new StreamInfo(1, null, new Settings(), new Settings());

    @Test
    void abortFailsBodyReadAfterReceivedData() throws Exception {
        final var body = streamInfo.createBody();
        final var data = new Frame();
        data.fill(TYPE_DATA, 0, 1, "ab".getBytes(), 0, 2);
        streamInfo.receivedDataFrame(data.asData());
        final var cause = new IOException("connection closed");

        streamInfo.abortReceiving(cause);

        final var e = assertThrows(ReadingBodyException.class, body::read);
        assertSame(cause, e.getCause());
    }

    /**
     * A streamed body is not retained, so exchange stores record what their observer collected from
     * the chunks passed to it, as for {@code Body} and {@code ChunkedBody}.
     */
    @Test
    void streamedBodyIsPassedToObservers() throws Exception {
        final var body = streamInfo.createBody();
        final var observer = new CollectingObserver();
        body.addObserver(observer);
        receiveData("Ice ", false);
        receiveData("cream", true);

        final var out = new ByteArrayOutputStream();
        body.write(new PlainBodyTransferer(out), false);

        assertEquals("Ice cream", out.toString(UTF_8));
        assertEquals("Ice cream", observer.collected);
    }

    /**
     * Observers get the chunks of a body that is read, too, as from {@code Body} and {@code ChunkedBody}.
     */
    @Test
    void readBodyIsPassedToObservers() throws Exception {
        final var body = streamInfo.createBody();
        final var chunks = new StringBuilder();
        body.addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyChunk(Chunk chunk) {
                chunks.append(new String(chunk.content(), 0, chunk.getLength(), UTF_8));
            }

            @Override
            public void bodyChunk(byte[] buffer, int offset, int length) {
                chunks.append(new String(buffer, offset, length, UTF_8));
            }
        });
        receiveData("Ice ", false);
        receiveData("cream", true);

        body.read();

        assertEquals("Ice cream", new String(body.getContent(), UTF_8));
        assertEquals("Ice cream", chunks.toString());
    }

    @Test
    void interruptFailsBodyRead() {
        final var body = streamInfo.createBody();
        Thread.currentThread().interrupt();
        try {
            final var e = assertThrows(ReadingBodyException.class, body::read);
            assertInstanceOf(InterruptedIOException.class, e.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    /** Nothing reads past the abort, so DATA still in flight would only take up memory until the stream is removed. */
    @Test
    void dataReceivedAfterAbortIsNotQueued() throws Exception {
        streamInfo.abortReceiving(new IOException("body rejected"));
        final var data = new Frame();
        data.fill(TYPE_DATA, 0, 1, "ab".getBytes(), 0, 2);

        streamInfo.receivedDataFrame(data.asData());

        assertEquals(1, streamInfo.getReceivedDataFramesQueued(), "Only the abort marker is queued.");
    }

    @Test
    void refusedStreamFailsBodyRead() {
        final var body = streamInfo.createBody();
        final var cause = new IOException("refused");

        streamInfo.refuse(cause);

        assertTrue(streamInfo.isRefused());
        assertSame(cause, assertThrows(ReadingBodyException.class, body::read).getCause());
    }

    /**
     * DATA beyond the content-length resets the stream, so the body never yields more than it declares.
     */
    @Test
    void bodyDeclaresTheContentLength() {
        streamInfo.expectContentLength(4);

        assertEquals(4, streamInfo.createBody().getDeclaredLength());
    }

    @Test
    void bodyWithoutContentLengthDeclaresNoLength() {
        assertEquals(-1, streamInfo.createBody().getDeclaredLength());
    }

    @Test
    void withoutContentLengthAnyBodyIsKept() {
        assertTrue(streamInfo.keepsContentLength(100, true));
    }

    @Test
    void bodyAsLongAsContentLengthIsKept() {
        streamInfo.expectContentLength(4);

        assertTrue(streamInfo.keepsContentLength(2, false));
        assertTrue(streamInfo.keepsContentLength(2, true));
    }

    @Test
    void longerBodyIsNotKeptBeforeItEnds() {
        streamInfo.expectContentLength(4);

        assertTrue(streamInfo.keepsContentLength(4, false));
        assertFalse(streamInfo.keepsContentLength(1, false));
    }

    @Test
    void shorterBodyIsNotKept() {
        streamInfo.expectContentLength(4);

        assertFalse(streamInfo.keepsContentLength(3, true));
    }

    @Test
    void violationIsReportedOnce() {
        streamInfo.expectContentLength(1);

        assertFalse(streamInfo.keepsContentLength(2, false));
        assertTrue(streamInfo.keepsContentLength(2, true));
    }

    private void receiveData(String data, boolean endStream) throws IOException {
        final var frame = new Frame();
        final var bytes = data.getBytes(UTF_8);
        frame.fill(TYPE_DATA, endStream ? FLAG_END_STREAM : 0, 1, bytes, 0, bytes.length);
        streamInfo.receivedDataFrame(frame.asData());
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
