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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A truncated "Transfer-Encoding: chunked" body fails inside the lazily reading stream returned by
 * {@link ChunkedBody#getContentAsStream()}. That failure must be recorded, so that the drain in
 * {@link ChunkedBody#read()} / {@link ChunkedBody#write} does not re-read the dead stream.
 */
public class ChunkedBodyStickyErrorTest {

    private ThrowingInputStream stream;
    private ChunkedBody body;

    @BeforeEach
    void setUp() {
        // a well-formed first chunk, then the client vanishes
        stream = new ThrowingInputStream("7\r\npartial\r\n".getBytes(), new ClosedChannelException());
        body = new ChunkedBody(stream);
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
}
