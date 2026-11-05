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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.tunnel.*;
import com.predic8.membrane.core.interceptor.websocket.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.ws.*;
import com.predic8.membrane.core.transport.ws.interceptors.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.net.http.*;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.*;
import static java.util.concurrent.TimeUnit.*;
import static org.junit.jupiter.api.Assertions.*;

class FramesResourceTest {

    private static final int BACKEND_PORT = 3097;
    private static final int PROXY_PORT = 3098;
    private static final String API = "/admin/api/v1";
    private static final ObjectMapper om = new ObjectMapper();

    private DefaultRouter router;
    private WebSocketRecordInterceptor recorder;

    @BeforeEach
    void setUp() throws Exception {
        router = new DefaultRouter();
        router.setExchangeStore(new LimitedMemoryExchangeStore());

        final var backend = new APIProxy();
        backend.setPort(BACKEND_PORT);
        backend.setFlow(new ArrayList<>(List.of(new WebSocketDemoStreamInterceptor())));
        router.add(backend);

        recorder = new WebSocketRecordInterceptor();
        final var webSocket = new WebSocketInterceptor();
        webSocket.setFlow(new ArrayList<>(List.of(recorder)));
        final var proxy = new APIProxy();
        proxy.setPort(PROXY_PORT);
        proxy.setFlow(new ArrayList<>(List.of(webSocket)));
        proxy.setTarget(new Target("localhost", BACKEND_PORT));
        router.add(proxy);
        router.start();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void framesOfARecordedConnection() throws Exception {
        final var exc = Request.get("/stream").buildExchange();
        final var frame = new WebSocketFrame(true, false, false, false, 1, false, new byte[0], "hello".getBytes(UTF_8));
        frame.setOriginalExchange(exc);
        recorder.handleFrame(frame, true, f -> {});
        final var binary = new WebSocketFrame(true, false, false, false, 2, false, new byte[0], new byte[]{(byte) 0xff, 0});
        binary.setOriginalExchange(exc);
        recorder.handleFrame(binary, false, f -> {});

        final var page = json(get(API + "/exchanges/node-a/" + exc.getId() + "/frames"));

        assertEquals(2, page.get("total").asInt());
        final var first = page.get("frames").get(0);
        assertEquals("clientToServer", first.get("direction").asText());
        assertEquals("text", first.get("opcode").asText());
        assertEquals("text", first.get("encoding").asText());
        assertEquals("hello", first.get("payload").asText());
        final var second = page.get("frames").get(1);
        assertEquals("binary", second.get("opcode").asText());
        assertEquals("base64", second.get("encoding").asText());
        assertEquals("/wA=", second.get("payload").asText());
    }

    @Test
    void unknownConnectionHasNoFrames() throws Exception {
        assertEquals(0, json(get(API + "/exchanges/node-a/42/frames")).get("total").asInt());
    }

    @Test
    void withoutRecorderTheApiTellsHowToRecord() throws Exception {
        final var plain = new TestRouter();
        final var api = new AdminApiV1Interceptor();
        api.setInstanceName("node-a");
        api.init(plain);
        final var exc = Request.get(API + "/exchanges/node-a/1/frames").buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        api.handleRequest(exc);
        assertEquals(501, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("wsRecord"));
    }

    /**
     * A real connection through Membrane: the upgraded exchange is listed as websocket, and its messages are recorded.
     */
    @Test
    void recordsMessagesOfARealConnection() throws Exception {
        final var received = new LinkedBlockingQueue<String>();
        try (var client = HttpClient.newHttpClient()) {
            final var ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:%d/stream".formatted(PROXY_PORT)), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            received.add(data.toString());
                            webSocket.request(1);
                            return null;
                        }
                    })
                    .get(5, SECONDS);
            assertNotNull(received.poll(5, SECONDS));
            ws.sendText("from client", true).get(5, SECONDS);

            final var upgraded = waitForUpgradedExchange();
            final var frames = waitForFrames(upgraded, 2);
            ws.abort();

            final var directions = new HashSet<String>();
            frames.forEach(f -> directions.add(f.get("direction").asText()));
            assertEquals(Set.of("clientToServer", "serverToClient"), directions);
            assertTrue(frames.toString().contains("from client"), frames.toString());
        }
    }

    private long waitForUpgradedExchange() throws Exception {
        for (int i = 0; i < 50; i++) {
            for (final var e : json(get(API + "/exchanges?statusCode=101")).get("exchanges"))
                if (e.get("websocket").asBoolean() && e.get("listenPort").asInt() == PROXY_PORT)
                    return e.get("id").asLong();
            Thread.sleep(100);
        }
        throw new AssertionError("No upgraded exchange was recorded.");
    }

    private JsonNode waitForFrames(long id, int atLeast) throws Exception {
        JsonNode frames = null;
        for (int i = 0; i < 50; i++) {
            frames = json(get(API + "/exchanges/node-a/" + id + "/frames")).get("frames");
            if (frames.size() >= atLeast && frames.toString().contains("from client"))
                return frames;
            Thread.sleep(100);
        }
        throw new AssertionError("Frames not recorded: " + frames);
    }

    private com.predic8.membrane.core.http.Response get(String uri) throws Exception {
        final var exc = Request.get(uri).buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        final var api = new AdminApiV1Interceptor();
        api.setInstanceName("node-a");
        api.init(router);
        api.handleRequest(exc);
        return exc.getResponse();
    }

    private static JsonNode json(com.predic8.membrane.core.http.Response res) throws Exception {
        assertEquals(200, res.getStatusCode(), res.getBodyAsStringDecoded());
        return om.readTree(res.getBodyAsStringDecoded());
    }
}
