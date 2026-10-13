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

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.*;

class Http2ClientTest {

    /** Length of the client connection preface plus the frame header of the SETTINGS frame sent after init(). */
    private static final int PREFACE_AND_SETTINGS_HEADER = 24 + 9;

    @Test
    void closeImmediatelyFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> client.closeImmediately());
    }

    @Test
    void closeFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> {
            // close() expects init() to have started the frame sender, which then sends SETTINGS.
            peer.getInputStream().readNBytes(PREFACE_AND_SETTINGS_HEADER);
            client.close();
        });
    }

    @Test
    void peerDisconnectFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> peer.close());
    }

    @Test
    void peerDisconnectAfterResponseKeepsResponse() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                // Runs on the calling thread: the response arrives and the connection is gone
                // before the call resumes, so both completions race for the same pending call.
                client.beforeResponseWait = () -> {
                    try {
                        // The preface implies the reader thread has started and is named.
                        peer.getInputStream().readNBytes(PREFACE_AND_SETTINGS_HEADER);
                        final var reader = findReaderThread(backend.getLocalPort());
                        peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM);
                        peer.getOutputStream().flush();
                        peer.close();
                        reader.join(2000);
                        assertFalse(reader.isAlive(), "Reader must have stopped after the disconnect");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                final var response = client.doCall(get("http://localhost/").header("Host", "localhost").buildExchange(), false);
                assertEquals(200, response.getStatusCode());
            }
        } finally {
            router.stop();
        }
    }

    /** HEADERS frame on stream 1 with END_STREAM and END_HEADERS, HPACK-encoded ":status: 200". */
    private static final byte[] RESPONSE_HEADERS_200_END_STREAM = {0, 0, 1, 0x1, 0x5, 0, 0, 0, 1, (byte) 0x88};

    private static Thread findReaderThread(int backendPort) {
        final var suffix = ":" + backendPort;
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("HTTP2 Client ") && t.getName().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No HTTP/2 client reader thread for port " + backendPort));
    }

    private static Connection createConnection(DefaultRouter router, ServerSocket backend) throws IOException {
        final var config = new HttpClientConfiguration();
        config.getConnection().setKeepAliveTimeout(60_000);
        return router.getHttpClientFactory().createClient(config).getConnectionFactory()
                .getConnectionManager().getConnection(
                        backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000);
    }

    private interface Termination {
        void terminate(Http2Client client, Socket peer) throws Exception;
    }

    private static void assertPendingCallFails(Termination termination) throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            // The peer accepts but never answers, so the call can only end through the termination.
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var waiting = new CountDownLatch(1);
                client.beforeResponseWait = waiting::countDown;
                final var exc = get("http://localhost/").header("Host", "localhost").buildExchange();
                final var call = CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.doCall(exc, false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // Terminate only once the call is pending, so the test covers failing it
                // rather than rejecting a call that starts after the close.
                assertTrue(waiting.await(2, TimeUnit.SECONDS), "Call must wait for its response");
                termination.terminate(client, peer);

                final var e = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, e.getCause().getCause());
            }
        } finally {
            router.stop();
        }
    }
}
