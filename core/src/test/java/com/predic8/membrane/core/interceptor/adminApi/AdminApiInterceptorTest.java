package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.FakeProxy;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.proxies.NullProxy;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.TwoWayStreaming;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static com.predic8.membrane.core.http.Header.CONNECTION;
import static com.predic8.membrane.core.http.Header.UPGRADE;
import static com.predic8.membrane.core.interceptor.adminApi.WebSocketConnection.computeKeyResponse;
import static org.junit.jupiter.api.Assertions.*;

class AdminApiInterceptorTest {

    @BeforeAll
    static void setUp() {
        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
        sp.getInterceptors().add(new AdminApiInterceptor());
        router.getRules().add(sp);
        router.start();
    }

    @Test
    public void testWebSocket() throws Exception {
        try (HttpClient client = new HttpClient()) {
            var mth = new MyTestHandler(0);
            var exc = Request.get("http://localhost:3065/ws/")
                    .header("Sec-WebSocket-Key", "0KhkDyGsK+qtDANJAp3lgQ==")
                    .header("Connection", "upgrade")
                    .header("Sec-WebSocket-Version", "13")
                    .header("Upgrade", "websocket")
                    .buildExchange(mth);
            exc.setProxy(new NullProxy());
            exc.setProperty(ALLOW_WEBSOCKET, Boolean.TRUE);

            client.call(exc);

            assertEquals(101, exc.getResponse().getStatusCode());
            assertEquals(UPGRADE, exc.getResponse().getHeader().getFirstValue(CONNECTION));
            assertEquals("websocket", exc.getResponse().getHeader().getFirstValue(UPGRADE));
            assertEquals("OETFqiZtABzji+GByUi/SEyzJS0=", exc.getResponse().getHeader().getFirstValue("Sec-WebSocket-Accept"));

            System.out.println(exc.getResponse());
        }
    }

    @Test
    public void testKeyResponse() {
        assertEquals("vvtlPs9jLaZ5KqY6wzvtYznMEpQ=",
                computeKeyResponse("+2chusljI/LtPLXb4+gMZg=="));
    }

    private static class MyTestHandler extends FakeHttpHandler implements TwoWayStreaming {
        public MyTestHandler(int port) {
            super(port);
        }

        @Override
        public InputStream getSrcIn() {
            return null;
        }

        @Override
        public OutputStream getSrcOut() {
            return null;
        }

        @Override
        public String getRemoteDescription() {
            return "test";
        }

        @Override
        public void removeSocketSoTimeout() throws SocketException {
            // do nothing
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() throws IOException {

        }
    }
}