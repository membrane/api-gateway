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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.predic8.membrane.annot.Constants.CRLF;
import static com.predic8.membrane.core.interceptor.FlowController.ABORTION_REASON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The reverse walk of a chain: which handler each interceptor gets and what the chain reports
 * back to its caller when the response flow aborts.
 */
class FlowControllerTest {

    private final List<String> calls = new ArrayList<>();

    /**
     * Released by the gateway just before it reads the backend's body, so it has received the headers.
     */
    private final CountDownLatch gatewayReadsBody = new CountDownLatch(1);

    private Exchange exchange;

    @BeforeEach
    void setUp() throws URISyntaxException {
        exchange = new Request.Builder().get("/foo").buildExchange();
    }

    private FlowController controller() {
        return new FlowController(new DummyTestRouter());
    }

    @Test
    void responseAbortUnwindsRemainingWithAbortHandlers() {
        List<Interceptor> chain = List.of(probe("a"), probe("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "c" aborted itself, so it does not get its own abort handler called
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseExceptionAbortsAndBuildsErrorResponse() {
        RuntimeException failure = new RuntimeException("boom");
        List<Interceptor> chain = List.of(probe("a"), probe("b"), throwingInResponse("c", failure));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
        assertNotNull(exchange.getResponse());
        assertEquals(500, exchange.getResponse().getStatusCode());
        assertSame(failure, exchange.getProperty(ABORTION_REASON));
    }

    @Test
    void abortHandlerFailureDoesNotStopUnwinding() {
        List<Interceptor> chain = List.of(probe("a"), failingInAbort("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "a" is still unwound although "b" threw in its abort handler
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseAbortAfterReturnIsReportedAsAbort() {
        List<Interceptor> chain = List.of(probe("a"), abortingInResponse("b"), returningInRequest("c"));

        assertEquals(ABORT, controller().invokeRequestHandlers(exchange, chain));

        assertEquals(List.of("a:request", "b:request", "c:request", "b:response", "a:abort"), calls);
    }

    @Test
    void abortUnwindingIgnoresTheAppliedFlow() {
        Probe requestOnly = probe("a");
        requestOnly.setAppliedFlow(EnumSet.of(Flow.REQUEST));
        List<Interceptor> chain = List.of(requestOnly, probe("b"), abortingInResponse("c"));

        assertEquals(ABORT, controller().invokeResponseHandlers(exchange, chain));

        // "a" handles no responses, but an aborting chain unwinds it all the same
        assertEquals(List.of("c:response", "b:abort", "a:abort"), calls);
    }

    @Test
    void responseFlowWithoutAbortContinues() {
        List<Interceptor> chain = List.of(probe("a"), probe("b"));

        assertEquals(CONTINUE, controller().invokeResponseHandlers(exchange, chain));

        assertEquals(List.of("b:response", "a:response"), calls);
    }

    @Test
    void productionModeKeepsExceptionDetailOutOfTheResponse() {
        Router production = DummyTestRouter.productionRouter();
        List<Interceptor> chain = List.of(probe("a"), throwingInResponse("c", new RuntimeException("boom")));

        assertEquals(ABORT, new FlowController(production).invokeResponseHandlers(exchange, chain));

        String body = exchange.getResponse().getBodyAsStringDecoded();
        assertFalse(body.contains("boom"), body);
    }

    /**
     * The backend announces more body bytes than it sends and closes the connection, so reading the
     * body fails on the gateway's side of the exchange, not the client's. In production the detail
     * is hidden, so the response has to point to the log entry through a log key.
     * <p>
     * Cause: java.net.SocketException: Connection reset
     */
    @Test
    void backendBodyTruncatedOverNetworkInProductionGivesLogKey() throws Exception {
        HttpResponse<String> response = getThroughGateway(true, socket -> socket.getOutputStream().write(
                (JSON_HEADER_1000 + "[{\"id\":").getBytes(US_ASCII)));

        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("See server log (key:"),
                "Expected a log key in the hidden detail, but got: " + response.body());
    }

    /**
     * A reset connection is explained by its message and the place named in the detail, so the
     * response carries no stack trace.
     * <p>
     * Cause: java.net.SocketException: Connection reset
     */
    @Test
    void backendResetsConnectionMidBody() throws Exception {
        HttpResponse<String> response = getThroughGateway(false, socket -> {
            drainRequest(socket);
            socket.getOutputStream().write((JSON_HEADER_1000 + "[{\"id\":").getBytes(US_ASCII));
            socket.getOutputStream().flush();
            awaitGatewayReadsBody();
            socket.setSoLinger(true, 0); // close() sends RST instead of FIN
        });

        assertBackendBodyFailure(response);
        assertTrue(response.body().contains("Connection reset"), response.body());
        assertFalse(response.body().contains("stackTrace"), response.body());
    }

    /**
     * A clean close before the announced Content-Length is an I/O error whose message says it all,
     * so the response carries no stack trace.
     * <p>
     * Cause: java.io.EOFException: Stream ended after 7 of 1000 expected bytes
     */
    @Test
    void backendClosesCleanlyBeforeContentLength() throws Exception {
        HttpResponse<String> response = getThroughGateway(false, socket -> {
            drainRequest(socket);
            socket.getOutputStream().write((JSON_HEADER_1000 + "[{\"id\":").getBytes(US_ASCII));
        });

        assertBackendBodyFailure(response);
        assertFalse(response.body().contains("stackTrace"), response.body());
    }

    /**
     * Cause: java.io.EOFException: Stream ended after 7 of 100 expected bytes
     */
    @Test
    void backendClosesInTheMiddleOfAChunk() throws Exception {
        HttpResponse<String> response = getThroughGateway(false, socket -> {
            drainRequest(socket);
            socket.getOutputStream().write(("HTTP/1.1 200 OK" + CRLF +
                    "Content-Type: application/json" + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Connection: close" + CRLF + CRLF +
                    "64" + CRLF + "[{\"id\":").getBytes(US_ASCII));
        });

        assertBackendBodyFailure(response);
        assertFalse(response.body().contains("stackTrace"), response.body());
    }

    /**
     * Cause: java.io.IOException: Chunk-size exceeds limit
     */
    @Test
    void backendSendsGarbageAsChunkSize() throws Exception {
        HttpResponse<String> response = getThroughGateway(false, socket -> {
            drainRequest(socket);
            socket.getOutputStream().write(("HTTP/1.1 200 OK" + CRLF +
                    "Content-Type: application/json" + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Connection: close" + CRLF + CRLF +
                    "not-a-size" + CRLF + "[{\"id\":1}]" + CRLF + "0" + CRLF + CRLF).getBytes(US_ASCII));
        });

        assertBackendBodyFailure(response);
    }

    private static void assertBackendBodyFailure(HttpResponse<String> response) {
        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("Could not read the response body from the backend."), response.body());
    }

    private static final String JSON_HEADER_1000 = "HTTP/1.1 200 OK" + CRLF +
            "Content-Type: application/json" + CRLF +
            "Content-Length: 1000" + CRLF +
            "Connection: close" + CRLF + CRLF;

    /**
     * What a raw backend does with an accepted connection.
     */
    private interface BackendBehavior {
        void serve(Socket socket) throws IOException;
    }

    /**
     * Calls a gateway whose only interceptor reads the backend's response body, so a broken body
     * fails inside the flow. The backend answers every connection with the given behavior, because
     * the client retries idempotent GETs.
     */
    private HttpResponse<String> getThroughGateway(boolean production, BackendBehavior behavior) throws Exception {
        try (ServerSocket backend = new ServerSocket(0)) {
            Thread backendThread = new Thread(() -> {
                while (!backend.isClosed()) {
                    try (Socket socket = backend.accept()) {
                        behavior.serve(socket);
                    } catch (IOException e) {
                        // Socket closed on teardown or peer gone
                    }
                }
            });
            backendThread.setDaemon(true);
            backendThread.start();

            int frontendPort = freePort();
            Router gateway = new TestRouter();
            gateway.getConfiguration().setProduction(production);
            try {
                ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey(frontendPort), "localhost", backend.getLocalPort());
                proxy.getFlow().add(new AbstractInterceptor() {
                    @Override
                    public Outcome handleResponse(Exchange exc) {
                        gatewayReadsBody.countDown();
                        exc.getResponse().getBody().getContent();
                        return CONTINUE;
                    }
                });
                gateway.add(proxy);
                gateway.start();

                return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + frontendPort + "/")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
            } finally {
                gateway.stop();
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Reads the request up to the empty line. Closing a socket with unread request bytes makes the
     * OS reset the connection instead of closing it cleanly.
     */
    private static void drainRequest(Socket socket) throws IOException {
        StringBuilder read = new StringBuilder();
        int b;
        while ((b = socket.getInputStream().read()) != -1) {
            read.append((char) b);
            if (read.length() >= 4 && read.substring(read.length() - 4).equals(CRLF + CRLF))
                return;
        }
    }

    /**
     * A reset sent before the gateway has read the response headers would discard them, and the
     * failure would no longer happen while reading the body.
     */
    private void awaitGatewayReadsBody() {
        try {
            gatewayReadsBody.await(5, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Probe probe(String name) {
        return new Probe(name);
    }

    private Probe returningInRequest(String name) {
        return new Probe(name).requestOutcome(RETURN);
    }

    private Probe abortingInResponse(String name) {
        return new Probe(name).responseOutcome(ABORT);
    }

    private Probe throwingInResponse(String name, RuntimeException failure) {
        return new Probe(name).responseFailure(failure);
    }

    private Probe failingInAbort(String name) {
        return new Probe(name).failInAbort();
    }

    /**
     * Records every handler it receives into the enclosing test's call log.
     */
    private class Probe extends AbstractInterceptor {

        private Outcome requestOutcome = CONTINUE;
        private Outcome responseOutcome = CONTINUE;
        private RuntimeException responseFailure;
        private boolean failInAbort;

        private Probe(String name) {
            setDisplayName(name);
        }

        private Probe requestOutcome(Outcome outcome) {
            requestOutcome = outcome;
            return this;
        }

        private Probe responseOutcome(Outcome outcome) {
            responseOutcome = outcome;
            return this;
        }

        private Probe responseFailure(RuntimeException failure) {
            responseFailure = failure;
            return this;
        }

        private Probe failInAbort() {
            failInAbort = true;
            return this;
        }

        @Override
        public Outcome handleRequest(Exchange exc) {
            calls.add(getDisplayName() + ":request");
            return requestOutcome;
        }

        @Override
        public Outcome handleResponse(Exchange exc) {
            calls.add(getDisplayName() + ":response");
            if (responseFailure != null)
                throw responseFailure;
            return responseOutcome;
        }

        @Override
        public void handleAbort(Exchange exc) {
            calls.add(getDisplayName() + ":abort");
            if (failInAbort)
                throw new RuntimeException("fail in abort");
        }
    }
}
