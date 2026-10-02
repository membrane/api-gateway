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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.transport.http2.Http2Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpClientFactoryShutdownTest {

    @Test
    void stalledHttp2SenderDoesNotBlockShutdownOrRuntimeReplacement() throws Exception {
        var router = new DefaultRouter();
        Connection connection = null;
        try (var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            var config = new HttpClientConfiguration();
            config.setUseExperimentalHttp2(true);
            config.getConnection().setKeepAliveTimeout(60_000);
            var factory = router.getHttpClientFactory();
            var client = factory.createClient(config);
            var connections = client.getConnectionFactory();
            connection = connections.getConnectionManager().getConnection(
                    backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000);
            try (var peer = backend.accept()) {
                var socket = connection.socket;
                var writing = new CountDownLatch(1);
                var writerExited = new CountDownLatch(1);
                var preface = new AtomicBoolean(true);
                // Let the client write its preface, then stall the actual frame
                // sender until the socket is closed. Interrupts alone do not help.
                connection.out = new OutputStream() {
                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (!preface.getAndSet(false))
                            write(0);
                    }

                    @Override
                    public void write(int value) throws IOException {
                        writing.countDown();
                        try {
                            while (!socket.isClosed()) {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException ignored) {
                                    // Model a socket write that requires socket closure to unblock.
                                }
                            }
                            throw new IOException("Socket closed");
                        } finally {
                            writerExited.countDown();
                        }
                    }

                    @Override
                    public void flush() throws IOException {
                        write(0);
                    }
                };
                var stalled = new Http2Client(connection, false);
                var other = mock(Http2Client.class);
                var pool = connections.getHttp2ClientPool();
                pool.share("localhost", 443, null, null, null, null, stalled);
                pool.share("localhost", 443, null, null, null, null, other);
                try {
                    assertTrue(writing.await(2, TimeUnit.SECONDS), "Frame sender must be stalled before shutdown");
                    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                        router.shutdownRuntimeComponents();
                        router.resetRuntime();
                    });
                    assertTrue(socket.isClosed());
                    assertTrue(writerExited.await(2, TimeUnit.SECONDS));
                    assertNotSame(factory, router.getHttpClientFactory());
                    verify(other).closeImmediately();
                } finally {
                    socket.close();
                    assertTrue(writerExited.await(2, TimeUnit.SECONDS));
                }
            }
        } finally {
            if (connection != null)
                connection.close();
            router.stop();
        }
    }

    @Test
    void connectionReturnedAfterShutdownIsClosed() throws Exception {
        var router = new DefaultRouter();
        Connection connection = null;
        try (var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            var manager = router.getHttpClientFactory().createClient(null)
                    .getConnectionFactory().getConnectionManager();
            connection = manager.getConnection(backend.getInetAddress().getHostAddress(),
                    backend.getLocalPort(), null, null, 2000);
            try (var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                router.stop();
                assertFalse(connection.isClosed(), "Borrowed HTTP/1 connections may finish their exchange");
                connection.release();
                assertTrue(connection.isClosed());
                assertEquals(0, manager.getNumberInPool());
                assertEquals(-1, peer.getInputStream().read());
                assertThrows(java.io.IOException.class, () -> manager.getConnection(
                        backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000));
            }
        } finally {
            try {
                if (connection != null)
                    connection.close();
            } finally {
                router.stop();
            }
        }
    }

    @Test
    void shutdownClosesHttp2PoolAndRejectsLateConnections() {
        var router = new DefaultRouter();
        var config = new HttpClientConfiguration();
        config.setUseExperimentalHttp2(true);
        config.getConnection().setKeepAliveTimeout(60_000);
        var pool = router.getHttpClientFactory().createClient(config).getConnectionFactory().getHttp2ClientPool();
        var pooled = mock(Http2Client.class);
        var late = mock(Http2Client.class);
        try {
            pool.share("localhost", 443, null, null, null, null, pooled);
            router.stop();
            verify(pooled).closeImmediately();
            assertNull(pool.reserveStream("localhost", 443, null, null, null, null));
            pool.share("localhost", 443, null, null, null, null, late);
            verify(late).closeImmediately();
            router.stop();
            verify(pooled, times(1)).closeImmediately();
        } finally {
            pool.closeAll();
            router.stop();
        }
    }

    @Test
    void resetRuntimeCreatesNewClientFactory() {
        var router = new DefaultRouter();
        try {
            var previous = router.getHttpClientFactory();
            var client = previous.createClient(null);
            router.shutdownRuntimeComponents();
            router.resetRuntime();
            assertNotSame(previous, router.getHttpClientFactory());
            assertNotSame(client, router.getHttpClientFactory().createClient(null));
        } finally {
            router.stop();
        }
    }

    @ParameterizedTest(name = "{0}, client.close() before shutdown: {1}")
    @CsvSource({
            "stop, false",
            "shutdownRuntimeComponents, false",
            "stop, true",
            "shutdownRuntimeComponents, true"
    })
    void shutdownClosesIdleConnectionWithoutWaitingForTimer(String shutdownMethod, boolean closeClientFirst)
            throws Exception {
        var router = new DefaultRouter();
        Connection connection = null;
        try (var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            var config = new HttpClientConfiguration();
            // Prevent normal idle expiry from masking missing shutdown cleanup.
            config.getConnection().setKeepAliveTimeout(60_000);
            var client = router.getHttpClientFactory().createClient(config);
            var manager = client.getConnectionFactory().getConnectionManager();
            connection = manager.getConnection(backend.getInetAddress().getHostAddress(),
                    backend.getLocalPort(), null, null, 2000);
            try (var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                connection.release();
                assertFalse(connection.isClosed(), "Connection must be idle in the pool before shutdown");
                assertEquals(1, manager.getNumberInPool());

                // Also reproduce why merely calling the existing close() before
                // cancelling the timer does not fix router shutdown.
                if (closeClientFirst)
                    client.close();
                switch (shutdownMethod) {
                    case "stop" -> router.stop();
                    case "shutdownRuntimeComponents" -> router.shutdownRuntimeComponents();
                    default -> throw new IllegalArgumentException(shutdownMethod);
                }

                assertTrue(connection.isClosed(), "Router shutdown must close pooled connections immediately");
                assertEquals(-1, peer.getInputStream().read(), "Backend must observe the connection closing");
            }
        } finally {
            // Release actual sockets even when the regression assertion fails.
            try {
                if (connection != null)
                    connection.close();
            } finally {
                router.stop();
            }
        }
    }
}
