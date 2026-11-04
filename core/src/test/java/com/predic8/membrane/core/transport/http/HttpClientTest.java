/* Copyright 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.Executors;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Request.post;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    private static final int TIMEOUT_MILLIS = 5_000;

    HttpClient client;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @Test
    void init() throws Exception {
        var exc = get("/foo").buildExchange();
        client.adjustHostHeader(exc,  new HostColonPort(false,HttpClient.getHostColonPort( "https://example.com")));
        assertEquals("example.com:443",exc.getRequest().getHeader().getHost());
    }

    @Test
    void setRequestURISimple() throws Exception {
        var request = get("/bar").build();
        client.setRequestURI(request, "https://predic8.de/foo", Mockito.mock(Connection.class));
        assertEquals("/foo", request.getUri());
    }

    @Test
    void setRequestURIWithoutPath() throws Exception {
        var request = get("/bar").build();
        client.setRequestURI(request, "https://predic8.de", Mockito.mock(Connection.class));
        assertEquals("/", request.getUri());
    }

    /**
     * The body of a POST is streamed instead of retained for a replay. A refused connection is the
     * failure the RetryHandler repeats a POST for, and it happens before anything was written, so the
     * next node still receives the complete body.
     */
    @Test
    void postBodyReachesNextNodeAfterConnectionRefused() throws Exception {
        final var refusedPort = closedPort();
        try (final var backend = new ServerSocket(0);
             final var executor = Executors.newSingleThreadExecutor();
             final var httpClient = new HttpClient()) {
            backend.setSoTimeout(TIMEOUT_MILLIS);
            final var receivedBody = executor.submit(() -> {
                try (final var peer = backend.accept()) {
                    peer.setSoTimeout(TIMEOUT_MILLIS);
                    final var request = new Request();
                    request.read(peer.getInputStream(), true);
                    final var body = request.getBodyAsStringDecoded();
                    peer.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(US_ASCII));
                    return body;
                }
            });
            final var payload = "hello".getBytes(US_ASCII);
            final var exc = post("/foo").body(payload.length, new ByteArrayInputStream(payload)).buildExchange();
            exc.setDestinations(List.of(
                    "http://localhost:%d/foo".formatted(refusedPort),
                    "http://localhost:%d/foo".formatted(backend.getLocalPort())));

            httpClient.call(exc);

            assertEquals("hello", receivedBody.get(TIMEOUT_MILLIS, MILLISECONDS));
            assertEquals(200, exc.getResponse().getStatusCode());
            assertTrue(exc.getRequest().getBody().wasStreamed(), "body must be streamed, not retained");
        }
    }

    /**
     * A port nobody listens on, so connecting to it is refused.
     */
    private static int closedPort() throws IOException {
        try (final var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
