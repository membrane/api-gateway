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
package com.predic8.membrane.core.interceptor.adminapi;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeCursor;
import com.predic8.membrane.core.exchangestore.ExchangeSortKey;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.http.AbstractBodyTransferer;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.NullProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.TwoWayStreaming;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

class AdminApiInterceptorTest {

    private static TestRouter router;
    private static AdminApiInterceptor interceptor;
    private static ServiceProxy sp;

    @BeforeAll
    static void setUp() throws IOException {
        router = new TestRouter();
        router.setExchangeStore(new LimitedMemoryExchangeStore()); // Needed by the AdminApiInterceptor
        sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
        interceptor = new AdminApiInterceptor();
        interceptor.getMemoryWatcher().setIntervalMilliseconds(50); // speeds up test execution
        sp.getFlow().add(interceptor);
        router.add(sp);
        router.start();
    }

    @AfterAll
    static void tearDown() {
        router.stop();
    }

    @Test
    void apiDetailsWithTrailingSlash() throws Exception {
        final var exc = get("/admin/api/unknown/").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(404, exc.getResponse().getStatusCode());
    }

    @Test
    void webSocket() throws Exception {
        try (HttpClient client = new HttpClient()) {
            var testHandler = new TestHandler();
            var exc = createWSRequest(testHandler);
            exc.setProxy(new NullProxy());
            exc.setProperty(ALLOW_WEBSOCKET, Boolean.TRUE);

            client.call(exc);

            assertEquals(101, exc.getResponse().getStatusCode());
            assertEquals(UPGRADE, exc.getResponse().getHeader().getFirstValue(CONNECTION));
            assertEquals("websocket", exc.getResponse().getHeader().getFirstValue(UPGRADE));
            assertEquals("OETFqiZtABzji+GByUi/SEyzJS0=", exc.getResponse().getHeader().getFirstValue(SEC_WEBSOCKET_ACCEPT));

            new Thread(exc::setCompleted).start(); // this starts the web socket pumps and blocks the thread created

            testHandler.outputStream.waitForData(); // this waits for WebSocket data to arrive

            parseAndVerifyWebSocketData(exc, testHandler.outputStream.toByteArray());

            testHandler.close(); // this interrupts the WebSocket connection, closing it down
            // This allows the HttpClient and HttpRouter to be shut down.
        }
    }

    @Test
    void liveCallsServerSentEvents() throws Exception {
        final var exc = get("/admin/calls/stream").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getHeader().getContentType().startsWith("text/event-stream"));

        final var transferer = new CollectingTransferer();
        final var streamer = new Thread(() -> exc.getResponse().getBody().write(transferer, false));
        streamer.start();

        assertEquals(ServerSentEventsExchangeWatcher.CONNECTED_COMMENT, transferer.chunks.poll(5, SECONDS));

        final var call = get("/shop/products/7").buildExchange();
        call.setProxy(sp);
        router.getExchangeStore().snap(call, REQUEST);

        final var event = transferer.chunks.poll(5, SECONDS);
        assertNotNull(event);
        assertTrue(event.startsWith("id: %d\nevent: call\ndata: {".formatted(call.getId())), event);
        assertTrue(event.contains("\"path\":\"/shop/products/7\""), event);
        assertTrue(event.endsWith("\n\n"), event);

        streamer.interrupt();
        assertTrue(transferer.finished.await(5, SECONDS));
        streamer.join(5000);
        assertFalse(streamer.isAlive());
    }

    @Test
    void callsCarryACursorToPageAfter() throws Exception {
        final var call = get("/shop/cursor-paging").buildExchange();
        call.setProxy(sp);
        router.getExchangeStore().snap(call, REQUEST);
        final var cursor = ExchangeCursor.of(call).encode();

        final var all = get("/admin/calls").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(all));
        assertTrue(all.getResponse().getBodyAsStringDecoded().contains("\"cursor\":\"%s\"".formatted(cursor)));

        final var after = get("/admin/calls?after=" + cursor).buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(after));
        assertEquals(200, after.getResponse().getStatusCode());
        assertFalse(after.getResponse().getBodyAsStringDecoded().contains("/shop/cursor-paging"));
    }

    @Test
    void invalidCursorIsABadRequest() throws Exception {
        final var exc = get("/admin/calls?after=garbage").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Invalid cursor"));
    }

    @Test
    void sortedCallsCarryTheSortValueInTheirCursor() throws Exception {
        final var call = get("/shop/sorted-paging").buildExchange();
        call.setProxy(sp);
        router.getExchangeStore().snap(call, REQUEST);
        final var next = get("/shop/sorted-paging/next").buildExchange();
        next.setProxy(sp);
        router.getExchangeStore().snap(next, REQUEST);
        final var cursor = ExchangeCursor.of(call, ExchangeSortKey.PATH).encode();
        assertTrue(cursor.endsWith("_/shop/sorted-paging"), cursor);

        final var sorted = get("/admin/calls?sort=path&order=asc").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(sorted));
        assertEquals(200, sorted.getResponse().getStatusCode());
        assertTrue(sorted.getResponse().getBodyAsStringDecoded().contains("\"cursor\":\"%s\"".formatted(cursor)));

        final var after = get("/admin/calls?sort=path&order=asc&after=" + encode(cursor, UTF_8)).buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(after));
        assertEquals(200, after.getResponse().getStatusCode());
        assertFalse(after.getResponse().getBodyAsStringDecoded().contains("\"/shop/sorted-paging\""));
        assertTrue(after.getResponse().getBodyAsStringDecoded().contains("\"/shop/sorted-paging/next\""));
    }

    @Test
    void callsShowTheDestinationOfAUrlTarget() throws Exception {
        final var target = new Target();
        target.setUrl("http://backend.example.com:3001");
        final var api = new ServiceProxy();
        api.setTarget(target);
        final var call = get("/shop/url-target").buildExchange();
        call.setProxy(api);
        // Like the dispatcher, which replaces the request URI with the target's URL.
        call.setDestinations(new ArrayList<>(List.of("http://backend.example.com:3001/shop/url-target")));
        router.getExchangeStore().snap(call, REQUEST);

        final var exc = get("/admin/calls?server=backend.example.com").buildExchange();
        assertEquals(RETURN, interceptor.handleRequest(exc));
        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("\"target\":{\"host\":\"backend.example.com\",\"port\":3001}"), body);
        assertTrue(body.contains("/shop/url-target"), body);
    }

    @Test
    void unsupportedSortIsABadRequest() throws Exception {
        for (final var query : List.of("sort=reqContentType", "sort=method&order=sideways")) {
            final var exc = get("/admin/calls?" + query).buildExchange();
            assertEquals(RETURN, interceptor.handleRequest(exc));
            assertEquals(400, exc.getResponse().getStatusCode(), query);
        }
    }

    @Test
    void liveCallsWatcherUnsubscribesOnDisconnect() throws Exception {
        final var watcher = new ServerSentEventsExchangeWatcher(10);
        final var finished = new CountDownLatch(1);
        final var streamer = new Thread(() -> watcher.createStreamingResponse().getBody().write(new AbstractBodyTransferer() {
            @Override
            public void write(byte[] content, int i, int length) {
            }

            @Override
            public void write(Chunk chunk) throws IOException {
                throw new IOException("client gone");
            }

            @Override
            public void finish(Header header) {
                finished.countDown();
            }
        }, false));
        streamer.start();

        assertTrue(finished.await(5, SECONDS));
        streamer.join(5000);
        assertEquals(0, watcher.getSubscriberCount());
    }

    private static class CollectingTransferer extends AbstractBodyTransferer {
        final LinkedBlockingQueue<String> chunks = new LinkedBlockingQueue<>();
        final CountDownLatch finished = new CountDownLatch(1);

        @Override
        public void write(byte[] content, int i, int length) {
            chunks.add(new String(content, i, length, UTF_8));
        }

        @Override
        public void write(Chunk chunk) {
            chunks.add(new String(chunk.content(), UTF_8));
        }

        @Override
        public void finish(Header header) {
            finished.countDown();
        }
    }

    private static void parseAndVerifyWebSocketData(Exchange exc, byte[] dataReceived) throws IOException {
        WebSocketFrameAssembler wsfa = new WebSocketFrameAssembler(new ByteArrayInputStream(dataReceived), exc);

        AtomicBoolean receivedMemoryStats = new AtomicBoolean();
        wsfa.readFrames(webSocketFrame -> {
            if (new String(webSocketFrame.getPayload()).contains("\"subject\":\"metricUpdate\""))
                receivedMemoryStats.set(true);
        });
        assertTrue(receivedMemoryStats.get());
    }

    private static Exchange createWSRequest(TestHandler testHandler) throws URISyntaxException {
        return Request.get("http://localhost:3065/ws/")
                .header(SEC_WEBSOCKET_KEY, "0KhkDyGsK+qtDANJAp3lgQ==")
                .header("Connection", "upgrade")
                .header("Sec-WebSocket-Version", "13")
                .header("Upgrade", "websocket")
                .buildExchange(testHandler);
    }

    /**
     * This Handler
     * 1. collects all streaming data received in a byte buffer
     * 2. supports waiting for data to be received (via <code>.outputStream.waitForData()</code>)
     * 3. has no data to transmit (=blocks indefinitely)
     * 4. only supports closing (which interrupts step 3)
     */
    private static class TestHandler extends FakeHttpHandler implements TwoWayStreaming {
        private volatile boolean closed = false;
        private final NotifyingByteArrayOutputStream outputStream = new NotifyingByteArrayOutputStream();
        private InputStream inputStream = new InputStream() {
            @Override
            public int read() {
                while (!closed) {
                    synchronized (TestHandler.this) {
                        try {
                            TestHandler.this.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                return -1;
            }
        };

        public TestHandler() {
            super(0);
        }

        @Override
        public InputStream getSrcIn() {
            return inputStream;
        }

        @Override
        public OutputStream getSrcOut() {
            return outputStream;
        }

        @Override
        public String getRemoteDescription() {
            return "test";
        }

        @Override
        public void removeSocketSoTimeout() {
            // do nothing
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}