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

import com.predic8.membrane.core.util.ExceptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A client that aborts while sending its body must produce exactly one exception, not a cascade of
 * follow-up errors from every component that touches the body afterwards.
 */
public class BodyStickyErrorTest {

    private ThrowingInputStream stream;
    private Body body;

    @BeforeEach
    void setUp() {
        stream = ThrowingInputStream.closedChannel("partial");
        body = new Body(stream, 15_000_000); // a large upload, as in the reported case
    }

    @Test
    void firstReadThrowsWithOriginalCause() {
        ReadingBodyException e = assertThrows(ReadingBodyException.class, body::read);

        assertInstanceOf(ClosedChannelException.class, ExceptionUtil.getRootCause(e));
        assertTrue(ExceptionUtil.isPeerDisconnect(e));
        assertTrue(body.hasFailed());
        assertFalse(body.isRead());
    }

    @Test
    void secondReadThrowsSameExceptionWithoutTouchingTheStream() {
        ReadingBodyException first = assertThrows(ReadingBodyException.class, body::read);

        ReadingBodyException second = assertThrows(ReadingBodyException.class, body::read);
        ReadingBodyException third = assertThrows(ReadingBodyException.class, body::read);

        assertSame(first, second);
        assertSame(first, third);
        assertEquals(0, stream.getReadCallsAfterFailure());
    }

    @Test
    void allAccessorsRethrowTheRecordedException() {
        ReadingBodyException first = assertThrows(ReadingBodyException.class, body::read);

        assertSame(first, body.getObservedException()); // identifies which body a failure belongs to
        assertSame(first, assertThrows(ReadingBodyException.class, body::getContent));
        assertSame(first, assertThrows(ReadingBodyException.class, body::getContentAsStream));
        assertSame(first, assertThrows(ReadingBodyException.class, body::getLength));
        assertSame(first, assertThrows(ReadingBodyException.class, body::getRaw));
        assertSame(first, assertThrows(ReadingBodyException.class,
                () -> body.write(new PlainBodyTransferer(new ByteArrayOutputStream()), false)));

        assertEquals(0, stream.getReadCallsAfterFailure());
    }

    @Test
    void discardAfterFailureIsSilent() {
        assertThrows(ReadingBodyException.class, body::read);

        assertDoesNotThrow(body::discard);
        assertEquals(0, stream.getReadCallsAfterFailure());
    }

    @Test
    void toStringAfterFailureDoesNotRead() {
        assertThrows(ReadingBodyException.class, body::read);

        assertDoesNotThrow(body::toString);
        assertEquals(0, stream.getReadCallsAfterFailure());
    }

    @Test
    void messageDiscardBodySwallowsTheRecordedException() {
        Request request = new Request();
        request.setBody(body);
        assertThrows(ReadingBodyException.class, body::read);

        assertDoesNotThrow(request::discardBody);
        assertEquals(0, stream.getReadCallsAfterFailure());
    }

    @Test
    void bodyErrorFiresOnceAndClearsObservers() {
        RecordingObserver observer = new RecordingObserver();
        body.addObserver(observer);

        assertThrows(ReadingBodyException.class, body::read);
        assertThrows(ReadingBodyException.class, body::read);

        assertEquals(1, observer.errors.size());
        assertEquals(0, observer.completions);
        assertTrue(body.getObservers().isEmpty());
    }

    @Test
    void observerSeesTheFailedStateWhileBeingNotified() {
        List<Boolean> latchWasArmed = new ArrayList<>();
        body.addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyError(ReadingBodyException e) {
                latchWasArmed.add(body.hasFailed());
            }
        });

        assertThrows(ReadingBodyException.class, body::read);

        assertEquals(List.of(true), latchWasArmed);
    }

    @Test
    void throwingObserverDoesNotMaskTheOriginalException() {
        RuntimeException fromObserver = new RuntimeException("observer is broken");
        body.addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyError(ReadingBodyException e) {
                throw fromObserver;
            }
        });

        ReadingBodyException e = assertThrows(ReadingBodyException.class, body::read);

        assertInstanceOf(ClosedChannelException.class, ExceptionUtil.getRootCause(e));
        assertArrayEquals(new Throwable[]{fromObserver}, e.getSuppressed());
    }

    @Test
    void lateObserverIsNotifiedImmediately() {
        assertThrows(ReadingBodyException.class, body::read);

        RecordingObserver observer = new RecordingObserver();
        body.addObserver(observer);

        assertEquals(1, observer.errors.size());
        assertTrue(body.getObservers().isEmpty());
    }

    private static class RecordingObserver extends AbstractMessageObserver {
        final List<ReadingBodyException> errors = new ArrayList<>();
        int completions;

        @Override
        public void bodyError(ReadingBodyException e) {
            errors.add(e);
        }

        @Override
        public void bodyComplete(AbstractBody body) {
            completions++;
        }
    }
}
