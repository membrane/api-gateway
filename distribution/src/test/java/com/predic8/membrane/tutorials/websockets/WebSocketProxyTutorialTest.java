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

package com.predic8.membrane.tutorials.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.examples.util.WaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;

import static io.restassured.RestAssured.when;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page is served by the backend on port 2001, the WebSocket connection goes through the proxy on port 2000.
 * RestAssured does not support WebSockets, so the stream is read with the Java HTTP client.
 */
public class WebSocketProxyTutorialTest extends AbstractWebSocketsTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "10-WebSocket-Proxy.yaml";
    }

    @Test
    void pageIsServedThroughTheProxy() {
        // @formatter:off
        when()
            .get("http://localhost:2000/")
        .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .body(containsString("new WebSocket(url)"));
        // @formatter:on
    }

    @Test
    void liveDataArrivesThroughTheProxy() throws Exception {
        final var messages = new LinkedBlockingQueue<String>();
        try (var client = HttpClient.newHttpClient()) {
            final var ws = connect(client, messages);

            final var om = new ObjectMapper();
            final var first = om.readTree(messages.poll(5, SECONDS));
            final var second = om.readTree(messages.poll(5, SECONDS));
            ws.abort();

            assertTrue(first.get("timestamp").isTextual());
            assertTrue(first.get("value").isNumber());
            assertEquals(first.get("message").asLong() + 1, second.get("message").asLong());
        }
    }

    @Test
    void proxyLogsMessages() throws Exception {
        final var consoleEvent = new WaitableConsoleEvent(process, line -> line.contains("payload={\"message\":"));

        try (var client = HttpClient.newHttpClient()) {
            final var ws = connect(client, new LinkedBlockingQueue<>());
            consoleEvent.waitFor(5_000);
            ws.abort();
        }

        assertTrue(consoleEvent.occurred(), "Expected wsLog to log a message from the backend");
    }

    private static WebSocket connect(HttpClient client, BlockingQueue<String> messages) throws Exception {
        return client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:2000/stream"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messages.add(data.toString());
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(5, SECONDS);
    }
}
