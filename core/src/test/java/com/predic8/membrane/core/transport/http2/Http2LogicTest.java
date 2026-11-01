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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http2.frame.FatalConnectionException;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class Http2LogicTest {

    /** SETTINGS frame with the ACK flag. */
    /** handle() does not start the frame sender, so the executor is never used. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final byte[] SETTINGS_ACK = {0, 0, 0, 0x4, 0x1, 0, 0, 0, 0};

    static Stream<Arguments> invalidFrames() {
        return Stream.of(
                arguments("PING with 7 bytes", new byte[]{0, 0, 7, 0x6, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7}),
                arguments("PING on a stream", new byte[]{0, 0, 8, 0x6, 0, 0, 0, 0, 1, 1, 2, 3, 4, 5, 6, 7, 8}),
                arguments("WINDOW_UPDATE of 0", new byte[]{0, 0, 4, 0x8, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
                arguments("CONTINUATION without HEADERS", new byte[]{0, 0, 1, 0x9, 0x4, 0, 0, 0, 1, (byte) 0x88}),
                arguments("PUSH_PROMISE", new byte[]{0, 0, 5, 0x5, 0x4, 0, 0, 0, 1, 0, 0, 0, 2, (byte) 0x88}),
                arguments("RST_STREAM on stream 0", new byte[]{0, 0, 4, 0x3, 0, 0, 0, 0, 0, 0, 0, 0, 0x8}),
                arguments("DATA on stream 0", new byte[]{0, 0, 1, 0x0, 0, 0, 0, 0, 0, 'a'}),
                arguments("DATA padded beyond its payload", new byte[]{0, 0, 2, 0x0, 0x8, 0, 0, 0, 1, 5, 'a'}),
                arguments("HEADERS padded beyond its payload", new byte[]{0, 0, 2, 0x1, 0x4 | 0x8, 0, 0, 0, 1, 5, (byte) 0x88}),
                arguments("SETTINGS with 5 bytes", new byte[]{0, 0, 5, 0x4, 0, 0, 0, 0, 0, 0, 0x3, 0, 0, 0}),
                arguments("GOAWAY with 4 bytes", new byte[]{0, 0, 4, 0x7, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
                // The client opened stream 3, but even ids belong to the server, which opened none: stream 2 is idle.
                arguments("WINDOW_UPDATE on an idle stream of the other parity", new byte[]{
                        0, 0, 3, 0x1, 0x5, 0, 0, 0, 3, (byte) 0x82, (byte) 0x84, (byte) 0x86,
                        0, 0, 4, 0x8, 0, 0, 0, 0, 2, 0, 0, 0, 1})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFrames")
    void invalidFrameIsConnectionError(String description, byte[] frame) {
        assertThrows(FatalConnectionException.class, () -> createLogic(frame).handle());
    }

    /** Only the SETTINGS frame sent on connection start is acknowledged; a second ACK has nothing to acknowledge. */
    @Test
    void unsolicitedSettingsAckIsProtocolError() throws Exception {
        final var in = new ByteArrayOutputStream();
        in.write(SETTINGS_ACK);
        in.write(SETTINGS_ACK);

        assertThrows(FatalConnectionException.class, () -> createLogic(in.toByteArray()).handle());
    }

    @Test
    void initAfterStopSendingDoesNotStartSender() throws Exception {
        final var logic = createLogic(new byte[0]);
        logic.stopSending();
        logic.init();

        assertNull(logic.senderFuture);
    }

    /** Field names and values RFC 9113 §8.2.1 requires to be treated as malformed. */
    static Stream<Arguments> malformedFields() {
        return Stream.of(
                arguments("upper-case name", bytes("X-Foo"), bytes("bar")),
                arguments("non-ASCII name", "authorızation".getBytes(UTF_8), bytes("secret")),
                arguments("name with space", bytes("x foo"), bytes("bar")),
                arguments("name with colon", bytes("x:foo"), bytes("bar")),
                arguments("name with control character", bytes("x\u0001foo"), bytes("bar")),
                // An empty name is not listed: the HPACK decoder already rejects it as a connection error.
                arguments("value with NUL", bytes("x-foo"), bytes("b\0ar")),
                arguments("value with CR", bytes("x-foo"), bytes("b\rar")),
                arguments("value with LF", bytes("x-foo"), bytes("b\nar")),
                arguments("value with leading space", bytes("x-foo"), bytes(" bar")),
                arguments("value with trailing tab", bytes("x-foo"), bytes("bar\t"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFields")
    void malformedFieldIsRejected(String description, byte[] name, byte[] value) throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestHeadersFrame(name, value), handler).handle());

        assertNull(handler.message, "The request must not be handed over.");
        assertEquals(1, handler.malformedStreamId);
    }

    /** A malformed message is a protocol error (RFC 9113 §8.1.1), even if it is also too large. */
    @Test
    void oversizedMalformedFieldIsRejectedAsMalformed() throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestHeadersFrame(bytes("X-Foo"), bytes("a".repeat(9000))), handler).handle());

        assertNull(handler.message, "The request must not be handed over.");
        assertEquals(1, handler.malformedStreamId);
        assertEquals(0, handler.oversizedStreamId);
    }

    @Test
    void lowerCaseTokenFieldIsAccepted() throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestHeadersFrame(bytes("x-foo"), bytes("b a\"r")), handler).handle());

        assertNotNull(handler.message);
        assertEquals("b a\"r", handler.message.getHeader().getFirstValue("x-foo"));
        assertEquals(0, handler.malformedStreamId);
    }

    /**
     * Values are read byte for byte, as by the HTTP/1.1 parser, whatever the default charset of the JVM is.
     */
    @Test
    void nonAsciiValueIsDecodedByteForByte() throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestHeadersFrame(bytes("x-foo"), new byte[]{'c', 'a', 'f', (byte) 0xE9}), handler).handle());

        assertNotNull(handler.message);
        assertEquals("café", handler.message.getHeader().getFirstValue("x-foo"));
    }

    /** Requests RFC 9113 §8.1.1, §8.2.2 and §8.3 require to be treated as malformed, each found by h2spec. */
    static Stream<Arguments> malformedRequests() {
        return Stream.of(
                arguments("unknown pseudo-header", (Object) withGet(":foo", "bar")),
                arguments("response pseudo-header", (Object) withGet(":status", "200")),
                arguments("pseudo-header after a regular field", (Object) fields(":method", "GET", ":scheme", "https", "x-foo", "bar", ":path", "/")),
                arguments("connection-specific field", (Object) withGet("connection", "keep-alive")),
                arguments("transfer-encoding", (Object) withGet("transfer-encoding", "chunked")),
                arguments("upgrade", (Object) withGet("upgrade", "websocket")),
                arguments("te other than trailers", (Object) withGet("te", "gzip")),
                arguments("empty :path", (Object) fields(":method", "GET", ":scheme", "https", ":path", "")),
                arguments("no :method", (Object) fields(":scheme", "https", ":path", "/")),
                arguments("no :scheme", (Object) fields(":method", "GET", ":path", "/")),
                arguments("no :path", (Object) fields(":method", "GET", ":scheme", "https")),
                arguments("duplicate :method", (Object) withGet(":method", "GET")),
                arguments("duplicate :scheme", (Object) fields(":method", "GET", ":scheme", "https", ":scheme", "https", ":path", "/")),
                arguments("duplicate :path", (Object) fields(":method", "GET", ":scheme", "https", ":path", "/", ":path", "/")),
                arguments("CONNECT without :authority", (Object) fields(":method", "CONNECT")),
                arguments("CONNECT with :path", (Object) fields(":method", "CONNECT", ":authority", "example.com:443", ":path", "/")),
                arguments("content-length but no body", (Object) withGet("content-length", "5")),
                arguments("invalid content-length", (Object) withGet("content-length", "abc"))
        );
    }

    /** The body of a request must be as long as its content-length (RFC 9113 §8.1.1). */
    static Stream<Arguments> bodiesNotMatchingContentLength() throws IOException {
        return Stream.of(
                arguments("longer DATA frame", "1", (Object) new byte[][]{dataFrame(true, "test")}),
                arguments("longer DATA frames", "3", (Object) new byte[][]{dataFrame(false, "ab"), dataFrame(true, "cd")}),
                arguments("shorter DATA frames", "10", (Object) new byte[][]{dataFrame(false, "ab"), dataFrame(true, "cd")}),
                arguments("shorter DATA frame before trailers", "10", (Object) new byte[][]{dataFrame(false, "ab"), headersFrame(true, new Encoder(4096), "x-foo", "bar")})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesNotMatchingContentLength")
    void bodyNotMatchingContentLengthFails(String description, String contentLength, byte[][] frames) throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestWithBody(contentLength, frames), handler).handle());

        assertNotNull(handler.message);
        assertThrows(ReadingBodyException.class, handler.message.getBody()::read);
    }

    @Test
    void bodyMatchingContentLengthIsRead() throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestWithBody("4", dataFrame(false, "te"), dataFrame(true, "st")), handler).handle());

        assertEquals("test", new String(handler.message.getBody().getContent(), ISO_8859_1));
    }

    /**
     * A POST request declaring the given content-length, followed by the given frames. The encoders of the header
     * blocks do not need to share state: they add literals to the dynamic table, but never refer to it.
     */
    private static byte[] requestWithBody(String contentLength, byte[]... frames) throws IOException {
        final var in = new ByteArrayOutputStream();
        in.write(headersFrame(false, new Encoder(4096), ":method", "POST", ":scheme", "https", ":path", "/", "content-length", contentLength));
        for (final var frame : frames)
            in.write(frame);
        return in.toByteArray();
    }

    /** DATA frame on stream 1. */
    private static byte[] dataFrame(boolean endStream, String payload) {
        final var frame = new ByteArrayOutputStream();
        frame.write(new byte[]{0, 0, (byte) payload.length(), 0x0, (byte) (endStream ? 0x1 : 0), 0, 0, 0, 1}, 0, 9);
        frame.writeBytes(bytes(payload));
        return frame.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedRequests")
    void malformedRequestIsRejected(String description, String[] fields) throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(headersFrame(true, new Encoder(4096), fields), handler).handle());

        assertNull(handler.message, "The request must not be handed over.");
        assertEquals(1, handler.malformedStreamId);
    }

    static Stream<Arguments> wellFormedRequests() {
        return Stream.of(
                arguments("te: trailers", (Object) withGet("te", "trailers")),
                arguments("CONNECT", (Object) fields(":method", "CONNECT", ":authority", "example.com:443"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wellFormedRequests")
    void wellFormedRequestIsAccepted(String description, String[] fields) throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(headersFrame(true, new Encoder(4096), fields), handler).handle());

        assertNotNull(handler.message);
        assertEquals(0, handler.malformedStreamId);
    }

    /** Trailers carry no pseudo-headers (RFC 9113 §8.1). The request was already handed over, so its body fails. */
    @Test
    void pseudoHeaderInTrailersFailsTheBody() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        in.write(headersFrame(false, encoder, withGet()));
        in.write(headersFrame(true, encoder, ":path", "/admin"));
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray(), handler).handle());

        assertNotNull(handler.message);
        assertEquals("/", ((Request) handler.message).getUri());
        assertThrows(ReadingBodyException.class, handler.message.getBody()::read);
    }

    /**
     * A stream beyond the advertised limit of 50 is refused (RFC 9113 §5.1.2). Its header block is decoded all the
     * same: the next request refers to the field it added to the HPACK dynamic table.
     */
    @Test
    void streamBeyondTheConcurrentStreamLimitIsRefused() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        // Acknowledges the SETTINGS sent on connection start, which announce the limit.
        in.write(SETTINGS_ACK);
        for (int streamId = 1; streamId <= 99; streamId += 2)
            in.write(headersFrame(streamId, false, encoder, withGet()));
        in.write(headersFrame(101, false, encoder, withGet("x-refused", "1")));
        // The client cancels stream 1, which makes room for one more.
        in.write(rstStream(1));
        in.write(headersFrame(103, false, encoder, withGet("x-refused", "1")));
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray(), handler).handle());

        assertEquals(51, handler.exchanges, "50 streams and the one after the cancel are handed over.");
        assertEquals("1", handler.message.getHeader().getFirstValue("x-refused"));
    }

    /**
     * The limit applies before the client acknowledged the SETTINGS that announce it: otherwise a client that delays
     * its ACK could open any number of streams.
     */
    @Test
    void concurrentStreamLimitAppliesBeforeTheSettingsAreAcknowledged() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        for (int streamId = 1; streamId <= 101; streamId += 2)
            in.write(headersFrame(streamId, false, encoder, withGet()));
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray(), handler).handle());

        assertEquals(50, handler.exchanges);
    }

    /**
     * A request still being processed counts against the concurrent stream limit, even after the client reset its
     * stream (CVE-2023-44487, "Rapid Reset"): resetting streams does not let the client start more work.
     */
    @Test
    void resetStreamCountsUntilItsExchangeFinished() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        in.write(SETTINGS_ACK);
        for (int streamId = 1; streamId <= 99; streamId += 2) {
            in.write(headersFrame(streamId, false, encoder, withGet()));
            in.write(rstStream(streamId));
        }
        in.write(headersFrame(101, false, encoder, withGet()));
        final var handler = new RecordingHandler();
        final var logic = createLogic(in.toByteArray(), handler);
        // Starts processing every request handed over, and finishes none.
        handler.logic = logic;

        assertThrows(EndOfStreamException.class, logic::handle);

        assertEquals(50, handler.exchanges, "The 51st request is refused while the first 50 are still processed.");
    }

    @Test
    void tooManyResetStreamsCloseTheConnection() throws Exception {
        assertThrows(FatalConnectionException.class, () -> createLogic(openAndResetStreams(Http2Logic.MAX_RESETS_PER_WINDOW + 1)).handle());
    }

    @Test
    void resetStreamsUpToTheLimitAreAccepted() throws Exception {
        assertThrows(EndOfStreamException.class, () -> createLogic(openAndResetStreams(Http2Logic.MAX_RESETS_PER_WINDOW)).handle());
    }

    /** The resets are counted in a sliding window: a burst spanning the end of a 30-second period is still too many. */
    @Test
    void tooManyResetStreamsWithinThirtySecondsCloseTheConnection() throws Exception {
        final var start = System.nanoTime();
        final var logic = createLogic(openAndResetStreams(Http2Logic.MAX_RESETS_PER_WINDOW + 1));
        final var resets = new AtomicInteger();
        // All but the last reset 29 seconds after the connection started, the last one 2 seconds later.
        logic.clock = () -> start + SECONDS.toNanos(resets.incrementAndGet() <= Http2Logic.MAX_RESETS_PER_WINDOW ? 29 : 31);

        assertThrows(FatalConnectionException.class, logic::handle);
    }

    @Test
    void resetStreamsOlderThanThirtySecondsAreNotCounted() throws Exception {
        final var start = System.nanoTime();
        final var logic = createLogic(openAndResetStreams(Http2Logic.MAX_RESETS_PER_WINDOW + 1));
        final var resets = new AtomicInteger();
        logic.clock = () -> start + SECONDS.toNanos(resets.incrementAndGet() <= Http2Logic.MAX_RESETS_PER_WINDOW ? 0 : 31);

        assertThrows(EndOfStreamException.class, logic::handle);
    }

    /** A refused stream that the client resets before the refusal was written counts as one reset, not two. */
    @Test
    void refusedStreamResetByTheClientCountsOnce() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        in.write(SETTINGS_ACK);
        for (int streamId = 1; streamId <= 99; streamId += 2)
            in.write(headersFrame(streamId, false, encoder, withGet()));
        for (int i = 0; i < Http2Logic.MAX_RESETS_PER_WINDOW; i++) {
            in.write(headersFrame(101 + 2 * i, false, encoder, withGet()));
            in.write(rstStream(101 + 2 * i));
        }

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray()).handle());
    }

    /**
     * HEADERS on a refused stream, arriving before its reset was written, are ignored. Their header block is decoded
     * all the same: the next request refers to the field it added to the HPACK dynamic table.
     */
    @Test
    void headersOnARefusedStreamAreIgnored() throws Exception {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        in.write(SETTINGS_ACK);
        for (int streamId = 1; streamId <= 99; streamId += 2)
            in.write(headersFrame(streamId, false, encoder, withGet()));
        in.write(headersFrame(101, false, encoder, withGet()));
        in.write(headersFrame(101, true, encoder, "x-trailer", "1"));
        in.write(rstStream(1));
        in.write(headersFrame(103, true, encoder, withGet("x-trailer", "1")));
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray(), handler).handle());

        assertEquals(51, handler.exchanges);
        assertEquals("1", handler.message.getHeader().getFirstValue("x-trailer"));
    }

    /**
     * CONTINUATION flood: a header block that does not end is buffered until its last fragment arrives. One that
     * grows too large closes the connection, before it uses up the memory.
     */
    @Test
    void headerBlockTooLargeToBufferClosesTheConnection() throws Exception {
        final var in = new ByteArrayOutputStream();
        in.write(frame(0x1, 0, new byte[0]));
        final var fragment = new byte[16000];
        for (int size = 0; size <= 3 * Http2Logic.MAX_HEADER_LIST_SIZE; size += fragment.length)
            in.write(frame(0x9, 0, fragment));

        assertThrows(FatalConnectionException.class, () -> createLogic(in.toByteArray()).handle());
    }

    /** CONTINUATION flood with empty frames, which never grow the block. */
    @Test
    void headerBlockInTooManyFragmentsClosesTheConnection() throws Exception {
        final var in = new ByteArrayOutputStream();
        in.write(frame(0x1, 0, new byte[0]));
        for (int i = 0; i < 1000; i++)
            in.write(frame(0x9, 0, new byte[0]));

        assertThrows(FatalConnectionException.class, () -> createLogic(in.toByteArray()).handle());
    }

    @Test
    void headerBlockInSeveralFragmentsIsAccepted() throws Exception {
        final var block = new ByteArrayOutputStream();
        final var encoder = new Encoder(4096);
        final var fields = withGet("x-foo", "bar");
        for (int i = 0; i < fields.length; i += 2)
            encoder.encodeHeader(block, bytes(fields[i]), bytes(fields[i + 1]), false);
        final var bytes = block.toByteArray();
        final var in = new ByteArrayOutputStream();
        // END_STREAM on HEADERS, END_HEADERS on the last of the CONTINUATION frames, each carrying 4 bytes.
        in.write(frame(0x1, 0x1, Arrays.copyOfRange(bytes, 0, 4)));
        for (int offset = 4; offset < bytes.length; offset += 4)
            in.write(frame(0x9, offset + 4 >= bytes.length ? 0x4 : 0, Arrays.copyOfRange(bytes, offset, Math.min(offset + 4, bytes.length))));
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(in.toByteArray(), handler).handle());

        assertEquals("bar", handler.message.getHeader().getFirstValue("x-foo"));
    }

    @Test
    void headerBlockInTheMostFragmentsAllowedIsAccepted() throws Exception {
        final var handler = new RecordingHandler();

        assertThrows(EndOfStreamException.class, () -> createLogic(requestInFragments(Http2Logic.MAX_HEADER_BLOCK_FRAMES), handler).handle());

        assertNotNull(handler.message);
    }

    @Test
    void headerBlockInOneFragmentTooManyClosesTheConnection() throws Exception {
        assertThrows(FatalConnectionException.class, () -> createLogic(requestInFragments(Http2Logic.MAX_HEADER_BLOCK_FRAMES + 1)).handle());
    }

    /** A GET request on stream 1, whose header block is carried by HEADERS followed by empty CONTINUATION frames. */
    private static byte[] requestInFragments(int frames) throws IOException {
        final var block = new ByteArrayOutputStream();
        final var encoder = new Encoder(4096);
        final var fields = withGet();
        for (int i = 0; i < fields.length; i += 2)
            encoder.encodeHeader(block, bytes(fields[i]), bytes(fields[i + 1]), false);
        final var in = new ByteArrayOutputStream();
        in.write(frame(0x1, 0x1, block.toByteArray()));
        for (int i = 1; i < frames; i++)
            in.write(frame(0x9, i == frames - 1 ? 0x4 : 0, new byte[0]));
        return in.toByteArray();
    }

    /** A frame on stream 1. */
    private static byte[] frame(int type, int flags, byte[] payload) {
        final var frame = new ByteArrayOutputStream();
        frame.write(new byte[]{(byte) (payload.length >>> 16), (byte) (payload.length >>> 8), (byte) payload.length, (byte) type, (byte) flags, 0, 0, 0, 1}, 0, 9);
        frame.writeBytes(payload);
        return frame.toByteArray();
    }

    /** Opens the given number of streams, each reset by the client right after its HEADERS frame. */
    private static byte[] openAndResetStreams(int count) throws IOException {
        final var encoder = new Encoder(4096);
        final var in = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            in.write(headersFrame(2 * i + 1, false, encoder, withGet()));
            in.write(rstStream(2 * i + 1));
        }
        return in.toByteArray();
    }

    /** RST_STREAM with error code CANCEL. */
    private static byte[] rstStream(int streamId) {
        return new byte[]{0, 0, 4, 0x3, 0, (byte) (streamId >>> 24), (byte) (streamId >>> 16), (byte) (streamId >>> 8), (byte) streamId, 0, 0, 0, 0x8};
    }

    /** The fields of a GET request, followed by the given ones. */
    private static String[] withGet(String... extra) {
        final var get = List.of(":method", "GET", ":scheme", "https", ":path", "/", ":authority", "localhost");
        return Stream.concat(get.stream(), Stream.of(extra)).toArray(String[]::new);
    }

    private static String[] fields(String... namesAndValues) {
        return namesAndValues;
    }

    /** HEADERS frame on stream 1 with END_HEADERS, carrying the given names and values in this order. */
    private static byte[] headersFrame(boolean endStream, Encoder encoder, String... namesAndValues) throws IOException {
        return headersFrame(1, endStream, encoder, namesAndValues);
    }

    private static byte[] headersFrame(int streamId, boolean endStream, Encoder encoder, String... namesAndValues) throws IOException {
        final var block = new ByteArrayOutputStream();
        for (int i = 0; i < namesAndValues.length; i += 2)
            encoder.encodeHeader(block, bytes(namesAndValues[i]), bytes(namesAndValues[i + 1]), false);
        final var frame = new ByteArrayOutputStream();
        frame.write(new byte[]{(byte) (block.size() >>> 16), (byte) (block.size() >>> 8), (byte) block.size(), 0x1, (byte) (endStream ? 0x5 : 0x4),
                (byte) (streamId >>> 24), (byte) (streamId >>> 16), (byte) (streamId >>> 8), (byte) streamId});
        block.writeTo(frame);
        return frame.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(ISO_8859_1);
    }

    /** HEADERS frame on stream 1 with END_STREAM and END_HEADERS: a GET request carrying the given field. */
    private static byte[] requestHeadersFrame(byte[] name, byte[] value) throws IOException {
        final var encoder = new Encoder(4096);
        final var block = new ByteArrayOutputStream();
        encoder.encodeHeader(block, bytes(":method"), bytes("GET"), false);
        encoder.encodeHeader(block, bytes(":scheme"), bytes("https"), false);
        encoder.encodeHeader(block, bytes(":path"), bytes("/"), false);
        encoder.encodeHeader(block, bytes(":authority"), bytes("localhost"), false);
        encoder.encodeHeader(block, name, value, false);
        final var frame = new ByteArrayOutputStream();
        frame.write(new byte[]{(byte) (block.size() >>> 16), (byte) (block.size() >>> 8), (byte) block.size(), 0x1, 0x5, 0, 0, 0, 1});
        block.writeTo(frame);
        return frame.toByteArray();
    }

    private static Http2Logic createLogic(byte[] input) {
        return createLogic(input, new RecordingHandler());
    }

    private static Http2Logic createLogic(byte[] input, Http2MessageHandler handler) {
        return new Http2Logic(EXECUTOR, new Socket(), new ByteArrayInputStream(input), new ByteArrayOutputStream(), false, handler);
    }

    private static class RecordingHandler implements Http2MessageHandler {
        Message message;
        int exchanges;
        /** If set, every request handed over is reported as being processed, and none as finished. */
        Http2Logic logic;
        int malformedStreamId;
        int oversizedStreamId;

        @Override
        public Message createMessage() {
            return new Request();
        }

        @Override
        public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
            this.message = message;
            exchanges++;
            if (logic != null)
                logic.exchangeStarted();
        }

        @Override
        public void handleOversizedHeaders(StreamInfo streamInfo, boolean endStream) {
            oversizedStreamId = streamInfo.getStreamId();
        }

        @Override
        public void handleMalformedHeaders(StreamInfo streamInfo) {
            malformedStreamId = streamInfo.getStreamId();
        }
    }
}
