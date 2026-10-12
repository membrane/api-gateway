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

package com.predic8.membrane.core.interceptor.tunnel;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.streampump.WebSocketStreamPump;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketInterceptorTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void invalidMaxMessageSizeIsRejected(int size) {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setMaxMessageSize(size);
        assertThrows(ConfigurationException.class, webSocket::init);
    }

    /**
     * One frame interceptor instance serves every connection: it is initialized once with the
     * configuration, not per connection.
     */
    @Test
    void frameInterceptorsAreInitializedOnceNotPerConnection() {
        final var counting = new CountingInitInterceptor();
        final var webSocket = new WebSocketInterceptor();
        webSocket.setFlow(List.of(counting));
        webSocket.init();

        final var api = new APIProxy();
        api.setFlow(List.of(webSocket));
        for (int connection = 0; connection < 2; connection++) {
            new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), null, "->", api, true, null);
            new WebSocketStreamPump(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), null, "<-", api, false, null);
        }

        assertEquals(1, counting.inits.get());
    }

    @Test
    void failingFrameInterceptorInitIsAConfigurationError() {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setFlow(List.of(new CountingInitInterceptor() {
            @Override
            public void init(Router router) throws Exception {
                throw new Exception("broken");
            }
        }));
        assertThrows(ConfigurationException.class, webSocket::init);
    }

    /**
     * Decides between parsing frames and relaying the connection's bytes as they are.
     */
    @Test
    void framesAreOnlyParsedForFrameInterceptors() {
        assertFalse(WebSocketInterceptor.hasFrameInterceptors(apiWith()));
        assertFalse(WebSocketInterceptor.hasFrameInterceptors(apiWith(new WebSocketInterceptor())));

        final var withFlow = new WebSocketInterceptor();
        withFlow.setFlow(List.of(new CountingInitInterceptor()));
        assertTrue(WebSocketInterceptor.hasFrameInterceptors(apiWith(withFlow)));
    }

    /**
     * Frame interceptors need uncompressed messages they may refragment, so no extension (e.g. permessage-deflate)
     * may be negotiated (RFC 6455 5.4). Without frame interceptors the connection is relayed as it is.
     */
    @Test
    void extensionsAreOnlyRemovedForFrameInterceptors() throws Exception {
        final var withFlow = new WebSocketInterceptor();
        withFlow.setFlow(List.of(new CountingInitInterceptor()));
        assertNull(upgradeThrough(withFlow).getRequest().getHeader().getFirstValue("Sec-WebSocket-Extensions"));

        assertEquals("permessage-deflate", upgradeThrough(new WebSocketInterceptor()).getRequest().getHeader().getFirstValue("Sec-WebSocket-Extensions"));
    }

    private static Exchange upgradeThrough(WebSocketInterceptor webSocket) throws Exception {
        final var exc = get("/ws")
                .header("Connection", "Upgrade")
                .header("Upgrade", "websocket")
                .header("Sec-WebSocket-Extensions", "permessage-deflate")
                .buildExchange();
        webSocket.handleRequest(exc);
        return exc;
    }

    private static APIProxy apiWith(Interceptor... flow) {
        final var api = new APIProxy();
        api.setFlow(List.of(flow));
        return api;
    }

    private static class CountingInitInterceptor implements WebSocketInterceptorInterface {

        final AtomicInteger inits = new AtomicInteger();

        @Override
        public void init(Router router) throws Exception {
            inits.incrementAndGet();
        }

        @Override
        public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
            sender.handleFrame(frame);
        }
    }
}
