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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2LogicTest {

    /** SETTINGS frame with the ACK flag. */
    private static final byte[] SETTINGS_ACK = {0, 0, 0, 0x4, 0x1, 0, 0, 0, 0};

    /** Only the SETTINGS frame sent on connection start is acknowledged; a second ACK has nothing to acknowledge. */
    @Test
    void unsolicitedSettingsAckIsProtocolError() throws Exception {
        final var in = new ByteArrayOutputStream();
        in.write(SETTINGS_ACK);
        in.write(SETTINGS_ACK);

        try (final var executor = Executors.newSingleThreadExecutor()) {
            final var logic = new Http2Logic(executor, new Socket(), new ByteArrayInputStream(in.toByteArray()),
                    new ByteArrayOutputStream(), false, new Http2MessageHandler() {
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

            assertThrows(FatalConnectionException.class, logic::handle);
        }
    }
}
