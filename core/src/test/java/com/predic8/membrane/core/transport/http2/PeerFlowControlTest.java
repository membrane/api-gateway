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

import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeerFlowControlTest {

    private final Settings settings = new Settings();
    private final FrameSender sender = new FrameSender(new ByteArrayOutputStream(), new Encoder(4096), settings, new ConcurrentHashMap<>(), "test");

    @Test
    void senderStopFailsReserveWaitingForWindow() throws Exception {
        final var flowControl = exhaustedConnectionFlowControl();
        final var blocked = reserveAsync(flowControl, new AtomicReference<>());
        assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));

        sender.stop();
        flowControl.senderStopped();

        final var e = assertThrows(ExecutionException.class, () -> blocked.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, e.getCause().getCause());
    }

    @Test
    void interruptFailsReserveWaitingForWindow() throws Exception {
        final var flowControl = exhaustedConnectionFlowControl();
        final var thread = new AtomicReference<Thread>();
        final var blocked = reserveAsync(flowControl, thread);
        assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));

        thread.get().interrupt();

        final var e = assertThrows(ExecutionException.class, () -> blocked.get(2, TimeUnit.SECONDS));
        assertInstanceOf(InterruptedIOException.class, e.getCause().getCause());
    }

    @Test
    void reserveGetsOnlyTheAvailableWindow() throws Exception {
        final var flowControl = new PeerFlowControl(0, sender, settings);
        flowControl.reserve(settings.getInitialWindowSize() - 3, 1);

        assertEquals(3, flowControl.reserve(10, 1));
    }

    @Test
    void reserveGetsAtMostTheWantedLength() throws Exception {
        assertEquals(10, new PeerFlowControl(0, sender, settings).reserve(10, 1));
    }

    @Test
    void unreserveWakesReserveWaitingForWindow() throws Exception {
        final var flowControl = exhaustedConnectionFlowControl();
        final var blocked = reserveAsync(flowControl, new AtomicReference<>());
        assertThrows(TimeoutException.class, () -> blocked.get(200, TimeUnit.MILLISECONDS));

        flowControl.unreserve(1);

        assertEquals(1, blocked.get(2, TimeUnit.SECONDS));
    }

    private PeerFlowControl exhaustedConnectionFlowControl() throws IOException {
        final var flowControl = new PeerFlowControl(0, sender, settings);
        flowControl.reserve(settings.getInitialWindowSize(), 1);
        return flowControl;
    }

    private static CompletableFuture<Integer> reserveAsync(PeerFlowControl flowControl, AtomicReference<Thread> thread) {
        return CompletableFuture.supplyAsync(() -> {
            thread.set(Thread.currentThread());
            try {
                return flowControl.reserve(1, 1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
