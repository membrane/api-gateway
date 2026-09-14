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
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.ReadingBodyException;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.util.ExceptionUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionUtilTest {

    @Test
    void matchesExceptionItselfWithoutVisitingItsCause() {
        var exception = new RuntimeException(new IOException());
        assertTrue(hasCauseMatching(exception, cause -> {
            assertSame(exception, cause);
            return true;
        }));
    }

    @Test
    void matchesNestedCauseAfterNonMatchingWrapperOfSameType() {
        var failure = new ReadingBodyException("broken body");
        var wrapper = new ReadingBodyException(new IOException(failure));
        List<Throwable> visited = new ArrayList<>();

        assertTrue(hasCauseMatching(wrapper, cause -> {
            visited.add(cause);
            return cause instanceof ReadingBodyException && cause == failure;
        }));
        assertEquals(List.of(wrapper, wrapper.getCause(), failure), visited);
    }

    @Test
    void returnsFalseWhenNoCauseMatches() {
        assertFalse(hasCauseMatching(new RuntimeException(new IOException()),
                cause -> cause instanceof IllegalArgumentException));
    }

    @Test
    void nullDoesNotInvokePredicate() {
        assertFalse(hasCauseMatching(null, cause -> {
            fail("An empty chain must not invoke the predicate");
            return true;
        }));
    }

    @Test
    void cyclicChainVisitsEachExceptionOnce() {
        var first = new RuntimeException("first");
        var second = new RuntimeException("second", first);
        first.initCause(second);
        List<Throwable> visited = new ArrayList<>();

        assertFalse(hasCauseMatching(first, cause -> {
            // Fail immediately on a repeated visit instead of hanging if cycle detection regresses.
            assertFalse(visited.contains(cause));
            visited.add(cause);
            return false;
        }));
        assertEquals(List.of(first, second), visited);
    }

    @Test
    void ignoresSuppressedExceptions() {
        var exception = new RuntimeException();
        exception.addSuppressed(new IOException());
        assertFalse(hasCauseMatching(exception, cause -> cause instanceof IOException));
    }

    @Test
    void propagatesPredicateFailure() {
        var failure = new IllegalStateException("predicate failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> hasCauseMatching(new RuntimeException(), cause -> { throw failure; })));
    }

    @Test
    public void testSimple() {
        assertEquals("foo",
                concatMessageAndCauseMessages(new RuntimeException("foo")));
    }

    @Test
    public void testLevel2() {
        assertEquals("foo caused by: bar",
                concatMessageAndCauseMessages(new RuntimeException("foo", new RuntimeException("bar"))));
    }
    @Test

    public void testLevel3() {
        assertEquals("foo caused by: bar caused by: baz",
                concatMessageAndCauseMessages(new RuntimeException("foo", new RuntimeException("bar", new RuntimeException("baz")))));
    }

    @Test
    void clientDisconnectClosedChannel() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(new ClosedChannelException())));
    }

    @Test
    void clientDisconnectNestedSeveralLevelsDeep() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(
                new ReadingBodyException(new IOException(new SocketException("Connection reset"))))));
    }

    @Test
    void serverSideDisconnectMatchesTheSameWay() {
        // the predicate is deliberately side-agnostic: a backend closing mid-response looks the same
        assertTrue(isPeerDisconnect(new ReadingBodyException(new SocketException("Connection reset"))));
    }

    @Test
    void clientDisconnectEndOfStream() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(new EOFException())));
    }

    @Test
    void plainIOExceptionIsNoClientDisconnect() {
        // e.g. Undertow's "UT010029: Stream is closed": deliberately not matched by message. It is no
        // longer reached anyway, now that a failed body read is sticky.
        assertFalse(isPeerDisconnect(new ReadingBodyException(new IOException("UT010029: Stream is closed"))));
    }

    @Test
    void genuineFaultIsNoClientDisconnect() {
        assertFalse(isPeerDisconnect(new ReadingBodyException(new IllegalStateException("broken"))));
    }

    @Test
    void nullIsNoClientDisconnect() {
        assertFalse(isPeerDisconnect(null));
    }

    @Test
    void connectExceptionIsNoPeerDisconnect() {
        // ConnectException extends SocketException, but an unreachable backend is a genuine fault
        assertFalse(isPeerDisconnect(new ConnectException("Connection refused")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(
                new IOException(new ConnectException("Connection refused")))));
    }

    @Test
    void noRouteToHostIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new NoRouteToHostException("No route to host")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(new NoRouteToHostException("No route to host"))));
    }

    @Test
    void portUnreachableIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new PortUnreachableException("ICMP port unreachable")));
    }

    @Test
    void bindExceptionIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new BindException("Address already in use")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(new BindException("Address already in use"))));
    }
}
