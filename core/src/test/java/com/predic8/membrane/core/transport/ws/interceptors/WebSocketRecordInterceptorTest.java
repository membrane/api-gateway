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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.ws.*;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketRecordInterceptorTest {

    @Test
    void recordsMessagesPerConnectionAndPassesThemOn() throws Exception {
        final var recorder = new WebSocketRecordInterceptor();
        final var a = exchange();
        final var b = exchange();
        final var forwarded = new ArrayList<WebSocketFrame>();

        recorder.handleFrame(text("hello", a), true, forwarded::add);
        recorder.handleFrame(text("world", a), false, forwarded::add);
        recorder.handleFrame(text("other", b), true, forwarded::add);

        assertEquals(3, forwarded.size());
        final var messages = recorder.connection(a.getId()).orElseThrow().messages();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).seq());
        assertTrue(messages.get(0).clientToServer());
        assertEquals("hello", new String(messages.get(0).payload(), UTF_8));
        assertFalse(messages.get(1).clientToServer());
        assertEquals(1, recorder.connection(b.getId()).orElseThrow().messages().size());
    }

    @Test
    void keepsOnlyTheNewestMessages() throws Exception {
        final var recorder = new WebSocketRecordInterceptor();
        recorder.setMaxMessages(2);
        final var a = exchange();
        for (int i = 1; i <= 5; i++)
            recorder.handleFrame(text("m" + i, a), true, f -> {});

        final var connection = recorder.connection(a.getId()).orElseThrow();
        assertEquals(List.of(4L, 5L), connection.messages().stream().map(WebSocketRecordInterceptor.RecordedMessage::seq).toList());
        assertEquals(5, connection.count());
    }

    @Test
    void truncatesLargePayloads() throws Exception {
        final var recorder = new WebSocketRecordInterceptor();
        recorder.setMaxPayloadBytes(3);
        final var a = exchange();
        recorder.handleFrame(text("abcdef", a), true, f -> {});

        final var m = recorder.connection(a.getId()).orElseThrow().messages().getFirst();
        assertEquals("abc", new String(m.payload(), UTF_8));
        assertEquals(6, m.length());
        assertTrue(m.truncated());
    }

    @Test
    void dropsLeastRecentlyActiveConnections() throws Exception {
        final var recorder = new WebSocketRecordInterceptor();
        recorder.setMaxConnections(2);
        final var a = exchange();
        final var b = exchange();
        final var c = exchange();
        recorder.handleFrame(text("1", a), true, f -> {});
        recorder.handleFrame(text("2", b), true, f -> {});
        recorder.handleFrame(text("3", a), true, f -> {});
        recorder.handleFrame(text("4", c), true, f -> {});

        assertTrue(recorder.connection(a.getId()).isPresent());
        assertTrue(recorder.connection(b.getId()).isEmpty());
        assertTrue(recorder.connection(c.getId()).isPresent());
    }

    @Test
    void recordedPayloadIsACopy() throws Exception {
        final var recorder = new WebSocketRecordInterceptor();
        final var a = exchange();
        final var frame = text("abc", a);
        recorder.handleFrame(frame, true, f -> {});
        frame.getPayload()[0] = 'X';
        assertEquals("abc", new String(recorder.connection(a.getId()).orElseThrow().messages().getFirst().payload(), UTF_8));
    }

    @Test
    void invalidLimitsAreAConfigurationError() {
        final var recorder = new WebSocketRecordInterceptor();
        recorder.setMaxMessages(0);
        assertThrows(ConfigurationException.class, () -> recorder.init(null));
    }

    private static Exchange exchange() throws Exception {
        return Request.get("/stream").buildExchange();
    }

    private static WebSocketFrame text(String s, Exchange exchange) {
        final var frame = new WebSocketFrame(true, false, false, false, 1, false, new byte[0], s.getBytes(UTF_8));
        frame.setOriginalExchange(exchange);
        return frame;
    }
}
