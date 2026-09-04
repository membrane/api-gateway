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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A truncated "Transfer-Encoding: chunked" body fails inside the lazily reading stream returned by
 * {@link ChunkedBody#getContentAsStream()}. That failure must be recorded, so that the drain in
 * {@link ChunkedBody#read()} / {@link ChunkedBody#write} does not re-read the dead stream.
 */
class ChunkedBodyStickyErrorTest {

    private ThrowingInputStream stream;
    private ChunkedBody body;

    @BeforeEach
    void setUp() {
        // a well-formed first chunk, then the client vanishes
        stream = new ThrowingInputStream("7\r\npartial\r\n".getBytes(), new ClosedChannelException());
        body = new ChunkedBody(stream);
    }

    @Test
    void markAsReadLeavesAFailedBodyIncomplete() throws IOException {
        var in = body.getContentAsStream();
        in.readNBytes(7);
        assertThrows(IOException.class, in::read);

        // markAsRead() must not be able to complete a failed body: AbstractBody refuses the
        // transition and therefore never runs onMarkedAsRead(), so bodyComplete stays false.
        // Were it set, readNextChunk()'s leading bodyComplete check would swallow the failure.
        body.markAsRead();

        assertFalse(body.bodyComplete);
        assertFalse(body.isRead());
        assertTrue(body.hasFailed());
    }

    @Test
    void markAsReadCompletesAHealthyBody() {
        ChunkedBody healthy = new ChunkedBody(new ByteArrayInputStream("0\r\n\r\n".getBytes()));

        healthy.read();

        // the hook ran, so the subclass flag and the base state agree
        assertTrue(healthy.bodyComplete);
        assertTrue(healthy.isRead());
        assertFalse(healthy.hasFailed());
    }

    @Test
    void failureInTheLazyStreamIsRecorded() throws IOException {
        var in = body.getContentAsStream();

        assertArrayEquals("partial".getBytes(), in.readNBytes(7));
        assertThrows(IOException.class, in::read); // InputStream contract is preserved
        assertTrue(body.hasFailed());
        assertFalse(body.isRead());
    }

    @Test
    void readAfterFailureDoesNotDrainTheDeadStream() throws IOException {
        var in = body.getContentAsStream();
        in.readNBytes(7);
        assertThrows(IOException.class, in::read);
        int readsSoFar = stream.getReadCallsAfterFailure();

        ReadingBodyException e = assertThrows(ReadingBodyException.class, body::read);

        assertInstanceOf(ClosedChannelException.class, e.getCause());
        assertEquals(readsSoFar, stream.getReadCallsAfterFailure());
        assertSame(e, assertThrows(ReadingBodyException.class, body::read));
    }

    @Test
    void writeAfterFailureDoesNotEmitATruncatedBody() throws IOException {
        var in = body.getContentAsStream();
        in.readNBytes(7);
        assertThrows(IOException.class, in::read);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(ReadingBodyException.class, () -> body.write(new PlainBodyTransferer(out), false));

        assertEquals(0, out.size());
    }

    @Test
    void discardAfterFailureIsSilent() throws IOException {
        var in = body.getContentAsStream();
        in.readNBytes(7);
        assertThrows(IOException.class, in::read);

        assertDoesNotThrow(body::discard);
    }

    @Test
    void getContentAsStreamAfterFailureDoesNotDropTheChunksRead() throws IOException {
        var in = body.getContentAsStream();
        in.readNBytes(7);
        assertThrows(IOException.class, in::read);
        int chunksRead = body.chunks.size();

        assertThrows(ReadingBodyException.class, body::getContentAsStream);

        assertEquals(chunksRead, body.chunks.size());
    }

    /**
     * The onMarkedAsRead() hook runs before the observers are notified, so a re-entering observer sees a
     * consistent body: isRead() and bodyComplete already agree. Notably MessageSnapshot's observer reads
     * the body again from inside bodyComplete(); the leading bodyComplete check in readNextChunk() then
     * gives it a clean EOF instead of re-reading the exhausted wire stream.
     */
    @Test
    void observerSeesACompletedBodyWhileBeingNotified() throws IOException {
        ChunkedBody healthy = new ChunkedBody(new ByteArrayInputStream("7\r\npartial\r\n0\r\n\r\n".getBytes()));
        List<String> observed = new ArrayList<>();
        healthy.addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                observed.add("isRead=" + body.isRead() + " bodyComplete=" + healthy.bodyComplete);
                try {
                    // re-entrant read, as MessageSnapshot does: must terminate rather than touch the wire
                    observed.add(new String(healthy.getContentAsStream().readAllBytes()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        healthy.read();

        assertEquals(List.of("isRead=true bodyComplete=true", "partial"), observed);
    }

    /**
     * The end-to-end shape of the attribution problem: the lazy stream throws the raw IOException (to
     * honour the InputStream contract), a caller wraps it again, and the handlers must still be able to
     * tell which end of the exchange died.
     */
    @Test
    void aWrappedFailureFromTheLazyStreamIsStillAttributable() throws IOException {
        Request request = new Request();
        request.setBody(body);

        var in = body.getContentAsStream();
        in.readNBytes(7);
        IOException fromStream = assertThrows(IOException.class, in::read);

        // as MessageUtil / HttpServerHandler.removeBodyFromBuffer() do
        ReadingBodyException rewrapped = new ReadingBodyException(fromStream);

        assertNotSame(body.getObservedException(), rewrapped);
        assertTrue(rewrapped.belongsTo(request));
    }
}
