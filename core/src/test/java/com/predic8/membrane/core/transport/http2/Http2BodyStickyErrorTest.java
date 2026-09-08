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

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.PlainBodyTransferer;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.transport.http2.frame.DataFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The body behind an HTTP/2 stream must record a failed read exactly like {@code Body} and
 * {@code ChunkedBody} do: first failure wins, and every later access re-throws that same exception
 * instead of pulling on the stream again.
 * <p>
 * <b>This is a contract test, not a regression test for a reachable defect.</b> The
 * {@link IOException} handled by {@code Http2Body} cannot currently be thrown in production: the
 * {@code throws IOException} chain behind {@link StreamInfo#removeDataFrame()} is vestigial -
 * {@code FlowControl.processed()} only calls {@code increaseWindow()} (pure arithmetic) and
 * {@code FrameSender.send(Frame)}, which queues rather than doing I/O. The compiler nevertheless
 * forces the catch to exist, so it is wired through the same failure latch as every other body, and
 * this test pins that wiring in case the path ever goes live.
 */
class Http2BodyStickyErrorTest {

    private static final IOException PEER_GONE = new IOException("connection reset");

    private CountingStreamInfo streamInfo;
    private AbstractBody body;

    @BeforeEach
    void setUp() {
        // null sender is safe: FlowControl only stores it, PeerFlowControl does not even do that
        streamInfo = new CountingStreamInfo();
        body = streamInfo.createBody();
    }

    @Test
    void readRecordsTheFailure() {
        ReadingBodyException e = assertThrows(ReadingBodyException.class, body::read);

        assertSame(PEER_GONE, e.getCause());
        assertTrue(body.hasFailed());
        assertFalse(body.isRead(), "a failed body never becomes read");
        assertSame(e, body.getObservedException());
    }

    @Test
    void secondReadRethrowsWithoutTouchingTheStream() {
        ReadingBodyException first = assertThrows(ReadingBodyException.class, body::read);
        assertEquals(1, streamInfo.removeCalls);

        ReadingBodyException second = assertThrows(ReadingBodyException.class, body::read);

        assertSame(first, second);
        assertEquals(1, streamInfo.removeCalls, "the dead stream must not be polled again");
    }

    @Test
    void streamingRecordsTheFailureAndEmitsNothing() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // retainCopy=false on an unread body routes to Http2Body.writeStreamed()
        ReadingBodyException e = assertThrows(ReadingBodyException.class,
                () -> body.write(new PlainBodyTransferer(out), false));

        assertSame(PEER_GONE, e.getCause());
        assertTrue(body.hasFailed());
        assertEquals(0, out.size(), "a body whose read failed must not be partially transmitted");
    }

    @Test
    void streamingAfterAFailedReadRethrowsTheOriginal() {
        ReadingBodyException first = assertThrows(ReadingBodyException.class, body::read);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ReadingBodyException second = assertThrows(ReadingBodyException.class,
                () -> body.write(new PlainBodyTransferer(out), false));

        assertSame(first, second, "the original cause must survive, not be replaced by a follow-up");
        assertEquals(1, streamInfo.removeCalls);
        assertEquals(0, out.size());
    }

    /**
     * Fails on the first data frame and counts how often it was asked, so the tests can assert that
     * a dead stream is not polled again.
     */
    private static class CountingStreamInfo extends StreamInfo {

        int removeCalls;

        CountingStreamInfo() {
            super(1, null, new Settings(), new Settings());
        }

        @Override
        public DataFrame removeDataFrame() throws IOException {
            removeCalls++;
            throw PEER_GONE;
        }
    }
}
