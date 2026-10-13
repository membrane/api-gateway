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

package com.predic8.membrane.core.transport.http.streampump;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.HttpServerHandler;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class StreamPumpTest {

    /**
     * An upgraded connection may be idle for any time: the backend's read timeout (<code>connection.soTimeout</code>)
     * must not end it, just as the client side has no read timeout after the upgrade.
     */
    @ParameterizedTest
    @ValueSource(strings = {"websocket", "tcp"})
    void backendReadTimeoutIsRemovedAfterUpgrade(String protocol) throws Exception {
        final var con = backendConnection(new byte[0]);
        final var exc = new Exchange(clientHandler());
        exc.setProxy(apiWithFrameFlow(new RecordingInterceptor()));

        StreamPump.setupConnectionForwarding(exc, con, protocol, null);

        verify(con.socket).setSoTimeout(0);
    }

    /**
     * Frame interceptors find the exchange of the upgrade request on frames of both directions.
     */
    @Test
    void serverFramesCarryTheOriginalExchange() throws Exception {
        final var recorder = new RecordingInterceptor();
        final var serverMessage = new WebSocketFrame(true, false, false, false, 1, false, new byte[4], "hi".getBytes(UTF_8)).toBytes();
        final var con = backendConnection(serverMessage);
        final var exc = new Exchange(clientHandler());
        exc.setProxy(apiWithFrameFlow(recorder));

        StreamPump.setupConnectionForwarding(exc, con, "websocket", null);
        exc.setCompleted(); // starts relaying

        assertSame(exc, recorder.originalExchange.get(10, SECONDS));
    }

    /**
     * Each router has its own statistics: stopping one router must not close the connections of another.
     */
    @Test
    void statsOfDifferentRoutersAreSeparate() {
        final var router1 = new StreamPump.StreamPumpStats();
        final var router2 = new StreamPump.StreamPumpStats();
        final var in = mock(InputStream.class);
        final var pump = new StreamPump(in, new ByteArrayOutputStream(), router1, "->", new APIProxy());

        router1.registerPump(pump);
        router2.closeAllStreamPumps();

        assertEquals(List.of(pump), router1.getStreamPumps());
        assertEquals(List.of(), router2.getStreamPumps());
        verifyNoInteractions(in);
    }

    private static HttpServerHandler clientHandler() {
        final var handler = mock(HttpServerHandler.class);
        when(handler.getSrcIn()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(handler.getSrcOut()).thenReturn(new ByteArrayOutputStream());
        return handler;
    }

    private static Connection backendConnection(byte[] fromServer) {
        final var con = mock(Connection.class);
        con.socket = mock(Socket.class);
        con.in = new ByteArrayInputStream(fromServer);
        con.out = new ByteArrayOutputStream();
        return con;
    }

    private static APIProxy apiWithFrameFlow(WebSocketInterceptorInterface frameInterceptor) {
        final var webSocket = new WebSocketInterceptor();
        webSocket.setFlow(List.of(frameInterceptor));
        final var api = new APIProxy();
        api.setFlow(List.of(webSocket));
        return api;
    }

    private static class RecordingInterceptor implements WebSocketInterceptorInterface {

        final CompletableFuture<Exchange> originalExchange = new CompletableFuture<>();

        @Override
        public void init(Router router) {
        }

        @Override
        public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
            if (!frameTravelsToRight)
                originalExchange.complete(frame.getOriginalExchange());
            sender.handleFrame(frame);
        }
    }
}
