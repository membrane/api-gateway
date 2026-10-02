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

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression tests for https://github.com/membrane/api-gateway/issues/3366. */
class CloseDelimitedResponseTest {

    private static final int TIMEOUT_MILLIS = 3000;

    @ParameterizedTest
    @ValueSource(strings = {"", "Connection: keep-alive\r\n"})
    void closeDelimitedResponseCannotKeepConnectionAlive(String connectionHeader) throws Exception {
        Response response = readResponse(responseBytes(connectionHeader));
        Exchange exchange = exchange(response);

        assertAll(
                () -> assertFalse(response.isKeepAlive(), "EOF is the only response body delimiter"),
                () -> assertFalse(exchange.canKeepConnectionAlive(), "the backend connection cannot be reused"));
    }

    @Test
    void reframingBufferedResponseDoesNotMakeBackendConnectionReusable() throws Exception {
        Response response = readResponse(responseBytes(""));
        response.setBodyContent(response.getBody().getContent());
        response.getHeader().setConnection("keep-alive");

        assertEquals(5, response.getHeader().getContentLength());
        assertFalse(response.isKeepAlive());
        assertFalse(exchange(response).canKeepConnectionAlive());
    }

    @Test
    void headResponseWithoutFramingRemainsKeepAlive() throws Exception {
        Response response = Response.fromStream(new ByteArrayInputStream(
                "HTTP/1.1 200 OK\r\n\r\n".getBytes(US_ASCII)), false);

        assertTrue(response.isKeepAlive());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Connection: keep-alive\r\n"})
    void streamingToEofClosesBackendConnectionInsteadOfPoolingIt(String connectionHeader) throws Exception {
        ConnectionManager pool = mock(ConnectionManager.class);
        // close() also notifies the manager for accounting; only an open connection is reusable.
        doAnswer(invocation -> {
            Connection released = invocation.getArgument(0);
            assertTrue(released.isClosed(), "must close before returning the connection to the manager");
            return null;
        }).when(pool).releaseConnection(any(Connection.class));
        try (ServerSocket backend = new ServerSocket(0);
             Connection connection = Connection.open("localhost", backend.getLocalPort(), null, null,
                     pool, TIMEOUT_MILLIS)) {
            connection.socket.setSoTimeout(TIMEOUT_MILLIS);
            try (Socket peer = backend.accept()) {
                peer.getOutputStream().write(responseBytes(connectionHeader));
            }
            Response response = new Response();
            response.read(connection.in, true);
            Exchange exchange = exchange(response);
            exchange.setTargetConnection(connection);
            connection.setExchange(exchange);
            response.addObserver(connection);

            // Exercise the streaming observer path, rather than discard(), which already closes correctly.
            ByteArrayOutputStream forwarded = new ByteArrayOutputStream();
            response.write(forwarded, true);
            assertTrue(forwarded.toString(US_ASCII).endsWith("hello"));
            assertAll(
                    () -> assertTrue(connection.isClosed(), "EOF-delimited backend connection must be closed"),
                    () -> assertNull(exchange.getTargetConnection()),
                    () -> verify(pool).releaseConnection(connection));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Connection: keep-alive\r\n"})
    void clientCanFinishReadingForwardedResponse(String connectionHeader) throws Exception {
        try (ServerSocket backend = new ServerSocket(0);
             var executor = Executors.newSingleThreadExecutor()) {
            backend.setSoTimeout(TIMEOUT_MILLIS);
            var backendTask = executor.submit(() -> {
                try (Socket peer = backend.accept()) {
                    peer.setSoTimeout(TIMEOUT_MILLIS);
                    // Consume the request before closing, so unread input cannot cause a TCP reset.
                    Request request = new Request();
                    request.read(peer.getInputStream(), true);
                    peer.getOutputStream().write(responseBytes(connectionHeader));
                }
                return null;
            });
            int port = freePort();
            TestRouter router = new TestRouter();
            router.add(new ServiceProxy(new ServiceProxyKey(port), "localhost", backend.getLocalPort()));
            try {
                router.start();
                try (Socket client = new Socket("localhost", port)) {
                    client.setSoTimeout(TIMEOUT_MILLIS);
                    client.getOutputStream().write(("GET / HTTP/1.1\r\nHost: localhost\r\n" +
                            "Connection: keep-alive\r\n\r\n").getBytes(US_ASCII));
                    Response response = new Response();
                    response.read(client.getInputStream(), true);
                    assertEquals(200, response.getStatusCode());
                    backendTask.get(3, SECONDS);

                    // Accept either explicit framing (length/chunked) or an actual connection close.
                    // Keeping an unframed response open currently times out here.
                    assertEquals("hello", assertDoesNotThrow(response::getBodyAsStringDecoded,
                            "the client must reach the response boundary without waiting for a timeout"));
                }
            } finally {
                router.stop();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "200 OK\r\nContent-Length: 5\r\n\r\nhello",
            "200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n",
            "204 No Content\r\n\r\n"
    })
    void selfDelimitedResponsesRemainKeepAlive(String statusHeadersAndBody) throws Exception {
        Response response = readResponse(("HTTP/1.1 " + statusHeadersAndBody).getBytes(US_ASCII));
        assertTrue(response.isKeepAlive());
        assertTrue(exchange(response).canKeepConnectionAlive());
    }

    private static byte[] responseBytes(String connectionHeader) {
        return ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" + connectionHeader +
                "\r\nhello").getBytes(US_ASCII);
    }

    private static Response readResponse(byte[] bytes) throws Exception {
        Response response = new Response();
        response.read(new ByteArrayInputStream(bytes), true);
        return response;
    }

    private static Exchange exchange(Response response) throws Exception {
        Request request = new Request();
        request.read(new ByteArrayInputStream("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                .getBytes(US_ASCII)), true);
        Exchange exchange = new Exchange(null);
        exchange.setRequest(request);
        exchange.setResponse(response);
        return exchange;
    }
}
