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

package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.predic8.membrane.test.TestAppender.captureLog;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketLogInterceptorTest {

    @Test
    void rawPayloadIsLoggedWithEscapedControlCharacters() throws Exception {
        final var frame = new WebSocketFrame(true, false, false, false, 1, false, new byte[0],
                "hello\n\u001b[31mFORGED log line".getBytes(UTF_8));
        final var forwarded = new ArrayList<WebSocketFrame>();

        final var log = captureLog(() -> {
            new WebSocketLogInterceptor().handleFrame(frame, true, forwarded::add);
            return null;
        });

        assertEquals(1, forwarded.size());
        assertTrue(log.contains("hello\\n\\u001b[31mFORGED log line"), log.getMessages().toString());
        assertFalse(log.contains("\nFORGED"), log.getMessages().toString());
        assertFalse(log.contains("\u001b"), log.getMessages().toString());
    }
}
