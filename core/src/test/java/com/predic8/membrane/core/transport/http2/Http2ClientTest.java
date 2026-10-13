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

import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_CANCEL;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_HEADERS;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_RST_STREAM;
import static org.junit.jupiter.api.Assertions.*;

class Http2ClientTest {

    /** Length of the HTTP/2 client connection preface. */
    private static final int PREFACE_LENGTH = 24;

    /** HEADERS frame on stream 1 with END_STREAM and END_HEADERS, HPACK-encoded ":status: 200". */
    private static final byte[] RESPONSE_HEADERS_200_END_STREAM = {0, 0, 1, 0x1, 0x5, 0, 0, 0, 1, (byte) 0x88};

    /** RST_STREAM frame on stream 1 with error code REFUSED_STREAM. */
    private static final byte[] RST_STREAM_1_REFUSED_STREAM = {0, 0, 4, 0x3, 0, 0, 0, 0, 1, 0, 0, 0, 0x7};

    @Test
    void closeImmediatelyFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> client.closeImmediately());
    }

    @Test
    void closeFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> client.close());
    }

    @Test
    void peerDisconnectFailsPendingCall() throws Exception {
        assertPendingCallFails((client, peer) -> peer.close());
    }

    @Test
    void peerResetFailsPendingCall() throws Exception {
        final var e = assertPendingCallFails((client, peer) -> {
            peer.getOutputStream().write(RST_STREAM_1_REFUSED_STREAM);
            peer.getOutputStream().flush();
        });
        assertTrue(e.getMessage().contains("reset by the peer"), e.getMessage());
    }

    @Test
    void unreadableRequestBodyCancelsStream() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var exc = post("http://localhost/").header("Host", "localhost").body(new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("Upload aborted");
                    }
                }).buildExchange();

                assertThrows(ReadingBodyException.class, () -> client.doCall(exc, false));

                final var in = peer.getInputStream();
                in.readNBytes(PREFACE_LENGTH);
                assertEquals(ERROR_CANCEL, readUntilFrame(in, TYPE_RST_STREAM)[3]);
                assertTrue(client.isIdle(), "A cancelled stream must not keep the connection busy");
            }
        } finally {
            router.stop();
        }
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
                final var call = callAsync(client);

                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM);
                peer.getOutputStream().flush();
                peer.close();

                assertEquals(200, call.get(2, TimeUnit.SECONDS).getStatusCode());
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void failureAfterResponseKeepsResponse() throws Exception {
        final var info = new Http2Client.ResponseInfo();
        final var response = Response.ok().build();
        info.complete(response);
        info.fail(new IOException("closed"));
        assertSame(response, info.await());
    }

    @Test
    void responseAfterFailureKeepsFailure() {
        final var info = new Http2Client.ResponseInfo();
        info.fail(new IOException("closed"));
        info.complete(Response.ok().build());
        assertThrows(IOException.class, info::await);
    }

    private static Connection createConnection(DefaultRouter router, ServerSocket backend) throws IOException {
        final var config = new HttpClientConfiguration();
        config.getConnection().setKeepAliveTimeout(60_000);
        return router.getHttpClientFactory().createClient(config).getConnectionFactory()
                .getConnectionManager().getConnection(
                        backend.getInetAddress().getHostAddress(), backend.getLocalPort(), null, null, 2000);
    }

    private static CompletableFuture<Response> callAsync(Http2Client client) throws URISyntaxException {
        final var exc = get("http://localhost/").header("Host", "localhost").buildExchange();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return client.doCall(exc, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Reads frames until the request HEADERS frame arrives. The client registers a call before sending
     * its HEADERS, so from then on the call is pending and must be ended by a close or a response.
     */
    private static void readUntilRequestHeaders(Socket peer) throws IOException {
        final var in = peer.getInputStream();
        in.readNBytes(PREFACE_LENGTH);
        readUntilFrame(in, TYPE_HEADERS);
    }

    /** Skips frames until one of the given type arrives and returns its payload. */
    private static byte[] readUntilFrame(InputStream in, int type) throws IOException {
        while (true) {
            final var header = in.readNBytes(9);
            if (header.length < 9)
                throw new EOFException("Connection closed before a frame of type %d arrived".formatted(type));
            final var payload = in.readNBytes(((header[0] & 0xff) << 16) | ((header[1] & 0xff) << 8) | (header[2] & 0xff));
            if (header[3] == type)
                return payload;
        }
    }

    private interface Termination {
        void terminate(Http2Client client, Socket peer) throws Exception;
    }

    private static IOException assertPendingCallFails(Termination termination) throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            // The peer accepts but never answers, so the call can only end through the termination.
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var call = callAsync(client);

                // Terminate only once the call is pending, so the test covers failing it
                // rather than rejecting a call that starts after the close.
                readUntilRequestHeaders(peer);
                termination.terminate(client, peer);

                final var e = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
                return assertInstanceOf(IOException.class, e.getCause().getCause());
            }
        } finally {
            router.stop();
        }
    }
}
