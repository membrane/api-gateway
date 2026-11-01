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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
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
        int malformedStreamId;
        int oversizedStreamId;

        @Override
        public Message createMessage() {
            return new Request();
        }

        @Override
        public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
            this.message = message;
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
