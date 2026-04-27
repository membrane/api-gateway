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

package com.predic8.membrane.tutorials.misc;

import com.predic8.membrane.examples.util.WaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Server-Sent Events (SSE) tutorial.
 *
 * <p>The tutorial configuration sets up:</p>
 * <ul>
 *   <li>Port 2000: Proxy with log interceptor → forwards to port 2001</li>
 *   <li>Port 2001 /stream: SSE demo stream (10 events, 1 s interval)</li>
 *   <li>Port 2001 /*: SSE debugger HTML page</li>
 * </ul>
 */
public class ServerSentEventsTutorialTest extends AbstractMiscTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "Server-Sent-Events.yaml";
    }

    /**
     * The SSE debugger HTML page must be served directly on port 2001.
     */
    @Test
    void debuggerPageIsServed() {
        // @formatter:off
        when()
            .get("http://localhost:2001/")
        .then()
            .statusCode(200)
            .contentType(containsString("text/html"));
        // @formatter:on
    }

    /**
     * A request to /stream via the proxy (port 2000) must return
     * Content-Type text/event-stream with HTTP 200.
     */
    @Test
    void sseProxyReturnsEventStreamContentType() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:2000/stream"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try (var client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            assertEquals(200, response.statusCode(), "Expected HTTP 200 from SSE proxy");

            var contentType = response.headers().firstValue("Content-Type").orElse("");
            assertTrue(contentType.contains("text/event-stream"),
                    "Expected Content-Type text/event-stream but got: " + contentType);

            // Close immediately – we only need the headers.
            response.body().close();
        }
    }

    /**
     * Reading the SSE stream through the proxy must deliver properly formatted
     * SSE events containing an id, an event name ("tick"), and a JSON data payload.
     */
    @Test
    void sseProxyStreamsWellFormedEvents() throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:2000/stream"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        boolean hasId        = false;
        boolean hasEventTick = false;
        boolean hasData      = false;

        try (var client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());

            try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                long deadline = System.currentTimeMillis() + 10_000;

                while ((line = reader.readLine()) != null && System.currentTimeMillis() < deadline) {
                    if (line.startsWith("id:"))     hasId        = true;
                    if (line.equals("event: tick")) hasEventTick = true;
                    if (line.startsWith("data:"))   hasData      = true;

                    // Stop as soon as we have seen one complete event.
                    if (hasId && hasEventTick && hasData) break;
                }
            }
        }

        assertTrue(hasId,        "SSE event must contain an 'id:' field");
        assertTrue(hasEventTick, "SSE event must contain 'event: tick'");
        assertTrue(hasData,      "SSE event must contain a 'data:' field with JSON payload");
    }

    /**
     * The log interceptor in the proxy must write SSE-related information to the console.
     * We wait for the log line that Membrane emits when it forwards the first SSE request.
     */
    @Test
    void proxyLogsRequest() throws Exception {
        var consoleEvent = new WaitableConsoleEvent(
                process,
                line -> line.contains("GET") && line.contains("/stream")
        );

        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:2000/stream"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try (var client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            response.body().close();
        }

        consoleEvent.waitFor(5_000);
        assertTrue(consoleEvent.occurred(),
                "Expected the proxy log interceptor to log the /stream request");
    }
}
