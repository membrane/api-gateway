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

package com.predic8.membrane.core.interceptor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketDemoStreamInterceptorTest {

    private static final int PORT = 3096;

    private DefaultRouter router;

    @BeforeEach
    void setUp() throws Exception {
        final var api = new APIProxy();
        api.setPort(PORT);
        api.setFlow(new ArrayList<>(List.of(new WebSocketDemoStreamInterceptor())));
        router = new DefaultRouter();
        router.add(api);
        router.start();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void connectedClientReceivesNumberedJsonMessages() throws Exception {
        final var messages = new LinkedBlockingQueue<String>();
        try (var client = HttpClient.newHttpClient()) {
            final var ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:%d/stream".formatted(PORT)), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            messages.add(data.toString());
                            webSocket.request(1);
                            return null;
                        }
                    })
                    .get(5, SECONDS);

            final var om = new ObjectMapper();
            final var first = om.readTree(messages.poll(5, SECONDS));
            final var second = om.readTree(messages.poll(5, SECONDS));
            ws.abort();

            assertTrue(first.get("timestamp").isTextual());
            assertTrue(first.get("value").isNumber());
            assertEquals(first.get("message").asLong() + 1, second.get("message").asLong());
            // one message per interval, not one per scheduled task
            assertTrue(Duration.between(Instant.parse(first.get("timestamp").asText()), Instant.parse(second.get("timestamp").asText())).toMillis() >= 900);
        }
    }

    @Test
    void requestWithoutUpgradeIsRejected() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            final var res = client.send(HttpRequest.newBuilder(URI.create("http://localhost:%d/stream".formatted(PORT))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, res.statusCode());
        }
    }

    @Test
    void intervalBelowOneSecondIsRejected() {
        final var demoStream = new WebSocketDemoStreamInterceptor();
        demoStream.setIntervalSeconds(0);
        assertThrows(ConfigurationException.class, demoStream::init);
    }
}
