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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http.client.RetryHandler;
import com.predic8.membrane.core.util.TimerManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    private final TimerManager timerManager = new TimerManager();
    HttpClientFactory f = new HttpClientFactory(timerManager);

    @AfterEach
    void stopTimer() {
        f.closeAll();
        timerManager.shutdown();
    }

    @Test
    void same() {
        assertSame(f.createClient(getConfig(1)), f.createClient(getConfig(1)));
    }

    @Test
    void different() {
        assertNotSame(f.createClient(getConfig(1)), f.createClient(getConfig(2)));
    }

    @Test
    void closedFactoryRejectsNewClients() {
        f.createClient(getConfig(1));
        f.closeAll();
        f.closeAll();
        assertThrows(IllegalStateException.class, () -> f.createClient(getConfig(1)));
    }

    @Test
    void sameClientAfterGarbageCollection() throws InterruptedException {
        HttpClient client = f.createClient(getConfig(1));

        // Observe collection of an unrelated weak reference so a JVM ignoring
        // System.gc() cannot silently make this regression test pass.
        var collected = new WeakReference<>(new Object());
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            System.gc();
            Thread.sleep(20);
        } while (collected.get() != null && System.nanoTime() < deadline);
        assertNull(collected.get(), "Test requires garbage collection to process weak references");

        assertSame(client, f.createClient(getConfig(1)),
                "A client still used by a caller must remain shared after garbage collection");
    }

    @Test
    void closingSharedClientKeepsIdleCleanup() throws Exception {
        final var config = getConfig(1);
        config.getConnection().setKeepAliveTimeout(100);
        // A consumer closing its shared client, e.g. try-with-resources around createClient().
        f.createClient(config).close();
        // Let a cleanup tick run on the empty pool; that is when a shut-down pool cancels its timer.
        Thread.sleep(500);

        final var connectionManager = f.createClient(config).getConnectionFactory().getConnectionManager();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final var connection = connectionManager.getConnection(
                    backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000);
            connectionManager.releaseConnection(connection);

            final var deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!connection.isClosed() && System.nanoTime() < deadline)
                Thread.sleep(50);
            assertTrue(connection.isClosed(), "Idle connection of a reused shared client must be closed after its keep-alive timeout");
        }
    }

    private static @NotNull HttpClientConfiguration getConfig(int retries) {
        return new HttpClientConfiguration() {{
           setRetryHandler(new RetryHandler() {{
               setRetries(retries);
           }});
        }};
    }
}
