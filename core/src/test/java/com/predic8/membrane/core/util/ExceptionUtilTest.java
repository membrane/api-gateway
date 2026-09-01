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
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;

import static com.predic8.membrane.core.util.ExceptionUtil.concatMessageAndCauseMessages;
import static com.predic8.membrane.core.util.ExceptionUtil.isPeerDisconnect;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionUtilTest {

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
    public void clientDisconnectClosedChannel() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(new ClosedChannelException())));
    }

    @Test
    public void clientDisconnectNestedSeveralLevelsDeep() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(
                new ReadingBodyException(new IOException(new SocketException("Connection reset"))))));
    }

    @Test
    public void serverSideDisconnectMatchesTheSameWay() {
        // the predicate is deliberately side-agnostic: a backend closing mid-response looks the same
        assertTrue(isPeerDisconnect(new ReadingBodyException(new SocketException("Connection reset"))));
    }

    @Test
    public void clientDisconnectEndOfStream() {
        assertTrue(isPeerDisconnect(new ReadingBodyException(new EOFException())));
    }

    @Test
    public void plainIOExceptionIsNoClientDisconnect() {
        // e.g. Undertow's "UT010029: Stream is closed": deliberately not matched by message. It is no
        // longer reached anyway, now that a failed body read is sticky.
        assertFalse(isPeerDisconnect(new ReadingBodyException(new IOException("UT010029: Stream is closed"))));
    }

    @Test
    public void genuineFaultIsNoClientDisconnect() {
        assertFalse(isPeerDisconnect(new ReadingBodyException(new IllegalStateException("broken"))));
    }

    @Test
    public void nullIsNoClientDisconnect() {
        assertFalse(isPeerDisconnect(null));
    }

    @Test
    public void connectExceptionIsNoPeerDisconnect() {
        // ConnectException extends SocketException, but an unreachable backend is a genuine fault
        assertFalse(isPeerDisconnect(new ConnectException("Connection refused")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(
                new IOException(new ConnectException("Connection refused")))));
    }

    @Test
    public void noRouteToHostIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new NoRouteToHostException("No route to host")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(new NoRouteToHostException("No route to host"))));
    }

    @Test
    public void portUnreachableIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new PortUnreachableException("ICMP port unreachable")));
    }

    @Test
    public void bindExceptionIsNoPeerDisconnect() {
        assertFalse(isPeerDisconnect(new BindException("Address already in use")));
        assertFalse(isPeerDisconnect(new ReadingBodyException(new BindException("Address already in use"))));
    }
}
