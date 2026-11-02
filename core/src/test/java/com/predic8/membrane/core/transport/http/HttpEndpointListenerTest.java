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

import com.predic8.membrane.core.util.Pair;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static com.predic8.membrane.core.transport.http.HttpEndpointListener.constructLogMessage;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpEndpointListenerTest {

    @Test
    void connectionLimitLogEscapesReceivedControlCharacters() throws Exception {
        final var received = "GET / HTTP/1.1\r\nX: \u001b[31m\n===END===\nFORGED log line\r\n\r\n".getBytes(UTF_8);
        final var buffer = new byte[1023];
        System.arraycopy(received, 0, buffer, 0, received.length);

        final var message = constructLogMessage(new StringBuilder(), InetAddress.getLoopbackAddress(), new Pair<>(buffer, received.length));

        assertTrue(message.contains("GET / HTTP/1.1\\r\\nX: \\u001b[31m\\n===END===\\nFORGED log line\\r\\n\\r\\n"), message);
        assertFalse(message.contains("\nFORGED"), message);
        assertFalse(message.contains("\u001b"), message);
    }
}
