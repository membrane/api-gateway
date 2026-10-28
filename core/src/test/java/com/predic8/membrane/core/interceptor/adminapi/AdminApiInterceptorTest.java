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
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.proxies.NullProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminApiInterceptorTest {

    private static TestRouter router;
    private static AdminApiInterceptor interceptor;

    @BeforeAll
    static void setUp() throws IOException {
        router = new TestRouter();
        router.setExchangeStore(new LimitedMemoryExchangeStore()); // Needed by the AdminApiInterceptor
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
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