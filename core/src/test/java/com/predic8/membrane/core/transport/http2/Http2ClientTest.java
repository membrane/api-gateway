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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_CANCEL;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.SettingsFrame.ID_SETTINGS_MAX_HEADER_LIST_SIZE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class Http2ClientTest {

    /** Length of the HTTP/2 client connection preface. */
    private static final int PREFACE_LENGTH = 24;

    /** Default HTTP/2 flow-control window of a stream and of the connection. */
    private static final int INITIAL_WINDOW_SIZE = 65535;

    /** Default maximum DATA frame payload. */
    private static final int MAX_FRAME_SIZE = 16384;

    /** HEADERS frame on stream 1 with END_STREAM and END_HEADERS, HPACK-encoded ":status: 200". */
    private static final byte[] RESPONSE_HEADERS_200_END_STREAM = {0, 0, 1, 0x1, 0x5, 0, 0, 0, 1, (byte) 0x88};

    /** HEADERS frame on stream 3 with END_STREAM and END_HEADERS, HPACK-encoded ":status: 200". */
    private static final byte[] RESPONSE_HEADERS_200_END_STREAM_3 = {0, 0, 1, 0x1, 0x5, 0, 0, 0, 3, (byte) 0x88};

    /** WINDOW_UPDATE frame on stream 1 with an increment of 1. */
    private static final byte[] WINDOW_UPDATE_1 = {0, 0, 4, 0x8, 0, 0, 0, 0, 1, 0, 0, 0, 1};

    /** HEADERS frame on stream 1 with END_HEADERS only, HPACK-encoded ":status: 200", so a body has to follow. */
    private static final byte[] RESPONSE_HEADERS_200 = {0, 0, 1, 0x1, 0x4, 0, 0, 0, 1, (byte) 0x88};

    /** DATA frame on stream 1 without END_STREAM, carrying "ab". */
    private static final byte[] DATA_1_AB = {0, 0, 2, 0x0, 0, 0, 0, 0, 1, 'a', 'b'};

    /** SETTINGS frame with MAX_CONCURRENT_STREAMS = 1. */
    private static final byte[] SETTINGS_MAX_CONCURRENT_STREAMS_1 = {0, 0, 6, 0x4, 0, 0, 0, 0, 0, 0, 0x3, 0, 0, 0, 1};

    /** SETTINGS frame with INITIAL_WINDOW_SIZE = 2^31-1, the largest value RFC 9113 §6.5.2 allows. */
    private static final byte[] SETTINGS_MAX_INITIAL_WINDOW_SIZE = {0, 0, 6, 0x4, 0, 0, 0, 0, 0, 0, 0x4, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};

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
    void closeImmediatelyFailsUploadBlockedByFlowControl() throws Exception {
        assertBlockedUploadFails((client, peer) -> client.closeImmediately());
    }

    @Test
    void closeFailsUploadBlockedByFlowControl() throws Exception {
        assertBlockedUploadFails((client, peer) -> client.close());
    }

    @Test
    void peerDisconnectFailsUploadBlockedByFlowControl() throws Exception {
        assertBlockedUploadFails((client, peer) -> peer.close());
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

    /**
     * A closed stream must not count against the peer's MAX_CONCURRENT_STREAMS, otherwise a pooled
     * connection can no longer be reused once that many requests have been sent over it.
     */
    @Test
    void closedStreamDoesNotCountAgainstConcurrentStreamLimit() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                peer.getOutputStream().write(SETTINGS_MAX_CONCURRENT_STREAMS_1);
                peer.getOutputStream().flush();
                final var client = new Http2Client(connection, false);

                // Like Http2ClientPool, reserve a stream before each call.
                assertTrue(client.reserveStream());
                final var call = callAsync(client);
                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM);
                peer.getOutputStream().flush();
                assertEquals(200, call.get(2, TimeUnit.SECONDS).getStatusCode());
                awaitIdle(client);

                assertTrue(client.reserveStream(), "The connection has no open stream, so it must accept another one");
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void largestInitialWindowSizeIsAccepted() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                peer.getOutputStream().write(SETTINGS_MAX_INITIAL_WINDOW_SIZE);
                peer.getOutputStream().flush();
                final var client = new Http2Client(connection, false);
                final var call = callAsync(client);

                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM);
                peer.getOutputStream().flush();

                assertEquals(200, call.get(2, TimeUnit.SECONDS).getStatusCode());
            }
        } finally {
            router.stop();
        }
    }

    /**
     * Interim 1xx responses precede the final response on the same stream (RFC 9113 §8.1). The call
     * has to return the final response, and the 1xx headers must not end up in it.
     */
    @Test
    void interimResponseIsSkipped() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var call = callAsync(client);

                readUntilRequestHeaders(peer);
                final var encoder = new Encoder(4096);
                peer.getOutputStream().write(headersFrame(1, 0x4, encoder, ":status", "103", "link", "</style.css>; rel=preload"));
                peer.getOutputStream().write(headersFrame(1, 0x5, encoder, ":status", "200", "x-final", "yes"));
                peer.getOutputStream().flush();

                final var response = call.get(2, TimeUnit.SECONDS);
                assertEquals(200, response.getStatusCode());
                assertEquals("yes", response.getHeader().getFirstValue("x-final"));
                assertNull(response.getHeader().getFirstValue("link"));
                assertFalse(response.getBody().hasTrailer());
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void oversizedResponseHeaderFieldFailsCall() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var call = callAsync(client);

                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(headersFrame(1, 0x4, new Encoder(4096), ":status", "200", "x-large", "a".repeat(9000)));
                peer.getOutputStream().flush();

                final var e = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
                final var cause = assertInstanceOf(IOException.class, e.getCause().getCause());
                assertTrue(cause.getMessage().contains("header size limit"), cause.getMessage());
                assertEquals(ERROR_CANCEL, readUntilFrame(peer.getInputStream(), TYPE_RST_STREAM)[3]);
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void clientAnnouncesMaxHeaderListSize() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                new Http2Client(connection, false);

                final var in = peer.getInputStream();
                in.readNBytes(PREFACE_LENGTH);
                final var settings = ByteBuffer.wrap(readUntilFrame(in, TYPE_SETTINGS));
                final var announced = new HashMap<Integer, Integer>();
                while (settings.hasRemaining())
                    announced.put((int) settings.getShort(), settings.getInt());

                assertEquals(Http2Logic.MAX_HEADER_LIST_SIZE, announced.get(ID_SETTINGS_MAX_HEADER_LIST_SIZE));
            }
        } finally {
            router.stop();
        }
    }

    /**
     * Frames the peer sends for a stream that is already closed (and therefore removed) must neither
     * break the connection nor be mistaken for frames on an idle stream.
     */
    @Test
    void framesOnClosedStreamAreIgnored() throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var first = callAsync(client);
                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM);
                peer.getOutputStream().flush();
                assertEquals(200, first.get(2, TimeUnit.SECONDS).getStatusCode());
                awaitIdle(client);

                peer.getOutputStream().write(WINDOW_UPDATE_1);
                peer.getOutputStream().write(RST_STREAM_1_REFUSED_STREAM);
                peer.getOutputStream().write(DATA_1_AB);
                peer.getOutputStream().flush();

                final var second = callAsync(client);
                readUntilFrame(peer.getInputStream(), TYPE_HEADERS);
                peer.getOutputStream().write(RESPONSE_HEADERS_200_END_STREAM_3);
                peer.getOutputStream().flush();
                assertEquals(200, second.get(2, TimeUnit.SECONDS).getStatusCode());
            }
        } finally {
            router.stop();
        }
    }

    @Test
    void peerDisconnectDuringResponseBodyFailsBodyRead() throws Exception {
        assertResponseBodyReadFails(Socket::close);
    }

    @Test
    void peerResetDuringResponseBodyFailsBodyRead() throws Exception {
        assertResponseBodyReadFails(peer -> {
            peer.getOutputStream().write(RST_STREAM_1_REFUSED_STREAM);
            peer.getOutputStream().flush();
        });
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
        return callAsync(client, get("http://localhost/").header("Host", "localhost").buildExchange());
    }

    private static CompletableFuture<Response> callAsync(Http2Client client, Exchange exc) {
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

    /** Reads DATA frames until the given number of payload bytes has arrived. */
    private static void readDataBytes(InputStream in, int length) throws IOException {
        var received = 0;
        while (received < length)
            received += readUntilFrame(in, TYPE_DATA).length;
    }

    /** Builds a HEADERS frame whose header block holds the given name/value pairs. */
    private static byte[] headersFrame(int streamId, int flags, Encoder encoder, String... namesAndValues) throws IOException {
        final var block = new ByteArrayOutputStream();
        for (var i = 0; i < namesAndValues.length; i += 2)
            encoder.encodeHeader(block, namesAndValues[i].getBytes(US_ASCII), namesAndValues[i + 1].getBytes(US_ASCII), false);
        final var frame = new ByteArrayOutputStream();
        frame.write(new byte[]{(byte) (block.size() >>> 16), (byte) (block.size() >>> 8), (byte) block.size(), TYPE_HEADERS, (byte) flags,
                (byte) (streamId >>> 24), (byte) (streamId >>> 16), (byte) (streamId >>> 8), (byte) streamId});
        block.writeTo(frame);
        return frame.toByteArray();
    }

    private static void awaitIdle(Http2Client client) throws InterruptedException {
        final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!client.isIdle()) {
            if (System.nanoTime() > deadline)
                fail("Stream was not closed after the response");
            Thread.sleep(10);
        }
    }

    private interface PeerAction {
        void run(Socket peer) throws Exception;
    }

    /**
     * The response headers arrive and part of the body, then the stream ends without END_STREAM.
     * Reading the rest of the body has to fail instead of waiting forever.
     */
    private static void assertResponseBodyReadFails(PeerAction endOfStream) throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                final var call = callAsync(client);

                readUntilRequestHeaders(peer);
                peer.getOutputStream().write(RESPONSE_HEADERS_200);
                peer.getOutputStream().write(DATA_1_AB);
                peer.getOutputStream().flush();
                final var response = call.get(2, TimeUnit.SECONDS);
                endOfStream.run(peer);

                // A daemon thread, so a body read that hangs does not keep the test JVM alive.
                final var read = new CompletableFuture<String>();
                final var reader = new Thread(() -> {
                    try {
                        read.complete(response.getBodyAsStringDecoded());
                    } catch (Throwable t) {
                        read.completeExceptionally(t);
                    }
                });
                reader.setDaemon(true);
                reader.start();

                assertThrows(ExecutionException.class, () -> read.get(5, TimeUnit.SECONDS));
            }
        } finally {
            router.stop();
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

    private static void assertBlockedUploadFails(Termination termination) throws Exception {
        final var router = new DefaultRouter();
        try (final var backend = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            backend.setSoTimeout(2000);
            final var connection = createConnection(router, backend);
            try (final var peer = backend.accept()) {
                peer.setSoTimeout(2000);
                final var client = new Http2Client(connection, false);
                // The peer never grants more window, so the upload blocks once the initial window is used up.
                final var body = new byte[2 * INITIAL_WINDOW_SIZE];
                final var call = callAsync(client, post("http://localhost/").header("Host", "localhost").body(body).buildExchange());

                final var in = peer.getInputStream();
                in.readNBytes(PREFACE_LENGTH);
                // Full frames only: the client reserves window per frame, so the frame that no longer fits blocks.
                readDataBytes(in, INITIAL_WINDOW_SIZE / MAX_FRAME_SIZE * MAX_FRAME_SIZE);
                termination.terminate(client, peer);

                assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
            }
        } finally {
            router.stop();
        }
    }
}
