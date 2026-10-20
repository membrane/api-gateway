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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

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

    private static Http2Logic createLogic(byte[] input) {
        return new Http2Logic(EXECUTOR, new Socket(), new ByteArrayInputStream(input), new ByteArrayOutputStream(), false, new Http2MessageHandler() {
            @Override
            public Message createMessage() {
                return new Request();
            }

            @Override
            public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
            }

            @Override
            public void handleOversizedHeaders(StreamInfo streamInfo, boolean endStream) {
            }
        });
    }
}
