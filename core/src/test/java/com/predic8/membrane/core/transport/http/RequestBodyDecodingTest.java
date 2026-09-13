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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.Target;
import com.predic8.membrane.core.router.TestRouter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static com.predic8.membrane.core.util.NetworkUtil.getFreePortEqualAbove;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class RequestBodyDecodingTest {

    private TestRouter router;
    private int port;
    private APIProxy proxy;

    @BeforeEach
    void setUp() throws Exception {
        port = getFreePortEqualAbove(3080);
        router = new TestRouter();
        proxy = new APIProxy();
        proxy.setPort(port);
        // Decode inside the request flow so the router's exception handling builds the response.
        // Return locally if decoding succeeds; no backend server is needed for this test.
        proxy.setFlow(new ArrayList<>(List.of(requestBodyDecoder(), new ReturnInterceptor())));
        router.add(proxy);
        router.start();
    }

    private static AbstractInterceptor requestBodyDecoder() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exchange) {
                // Trigger decoding without handling the exception in the interceptor itself.
                exchange.getRequest().getBodyAsStringDecoded();
                return Outcome.CONTINUE;
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (router != null)
            router.stop();
    }

    @Test
    void truncatedDeflateRequestReturns400WithReason() throws Exception {
        byte[] truncated = truncatedDeflateBody("payload");
        // Keep HTTP framing valid: the publisher sets Content-Length to the bytes actually sent.
        // Only the compressed content is incomplete, so decoding must cause the error.
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "text/plain")
                .header("Content-Encoding", "deflate")
                .POST(HttpRequest.BodyPublishers.ofByteArray(truncated))
                .build();

        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // Malformed client content must identify the decoding error in the 400 response.
            assertAll(
                    () -> assertEquals(400, response.statusCode(), response.body()),
                    () -> assertEquals("Truncated deflate stream.",
                            new ObjectMapper().readTree(response.body()).path("detail").asText(), response.body()));
        }
    }

    @Test
    void truncatedBackendDeflateResponseReturns500() throws Exception {
        byte[] truncated = truncatedDeflateBody("backend payload");
        var backend = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        backend.createContext("/", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Encoding", "deflate");
                exchange.sendResponseHeaders(200, truncated.length);
                exchange.getResponseBody().write(truncated);
            }
        });
        backend.start();
        try {
            proxy.setTarget(new Target("localhost", backend.getAddress().getPort()));
            // Read an actual backend response before the gateway sends its headers to the client.
            proxy.setFlow(new ArrayList<>(List.of(new AbstractInterceptor() {
                @Override
                public Outcome handleResponse(Exchange exchange) {
                    exchange.getResponse().getBodyAsStringDecoded();
                    return Outcome.CONTINUE;
                }
            })));
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(500, response.statusCode(), response.body());
                // Ensure this is the decoding failure, rather than an unrelated connection error.
                assertTrue(new ObjectMapper().readTree(response.body()).path("message").asText()
                        .contains("Truncated deflate stream."), response.body());
            }
        } finally {
            backend.stop(0);
        }
    }

    private static byte[] truncatedDeflateBody(String payload) throws IOException {
        var compressed = new ByteArrayOutputStream();
        // nowrap=true produces raw deflate, matching MessageUtil's decoder.
        var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try (var stream = new DeflaterOutputStream(compressed, deflater)) {
            stream.write(payload.getBytes(UTF_8));
        } finally {
            // The stream finishes compression on close; the supplied deflater is ours to release.
            deflater.end();
        }
        // Truncate only after compression finishes, removing the end of an otherwise valid stream.
        return Arrays.copyOf(compressed.toByteArray(), compressed.size() - 1);
    }
}
