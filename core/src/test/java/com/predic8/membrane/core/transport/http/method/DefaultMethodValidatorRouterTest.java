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

package com.predic8.membrane.core.transport.http.method;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.transport.http.HttpTransport;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@link com.predic8.membrane.core.transport.http.method.DefaultMethodValidator} - the policy
 * {@link com.predic8.membrane.core.transport.Transport} falls back to when no {@code methodValidator} component
 * is declared - is actually enforced end-to-end: a router with a running proxy rejects an invalid method before
 * it ever reaches the flow, and accepts a valid one.
 */
class DefaultMethodValidatorRouterTest {

    private static final int PORT = 3098;

    private DefaultRouter router;
    private boolean requestReachedFlow;

    @BeforeEach
    void setUp() throws Exception {
        requestReachedFlow = false;

        HttpTransport transport = new HttpTransport();
        transport.setSocketTimeout(2000);

        router = new DefaultRouter();
        router.setTransport(transport);
        router.add(createApiProxy());
        router.start();
    }

    @AfterEach
    void tearDown() {
        if (router != null)
            router.stop();
    }

    @Test
    void validMethodReachesTheFlow() throws Exception {
        String response = rawRequest("GET");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(requestReachedFlow);
    }

    @Test
    void invalidMethodIsRejectedBeforeTheFlow() throws Exception {
        // Digits are not allowed as the first character by DefaultMethodValidator, unlike the permissive
        // RFC 9110 token grammar.
        String response = rawRequest("1NVALID");

        assertTrue(response.startsWith("HTTP/1.1 501"), response);
        assertFalse(requestReachedFlow);
    }

    @Test
    void tooLongMethodIsRejectedBeforeTheFlow() throws Exception {
        // Otherwise well-formed (all ASCII letters), but longer than DefaultMethodValidator's default
        // maxLength of 20.
        String response = rawRequest("A".repeat(21));

        assertTrue(response.startsWith("HTTP/1.1 501"), response);
        assertFalse(requestReachedFlow);
    }

    private String rawRequest(String method) throws Exception {
        try (Socket s = new Socket("localhost", PORT)) {
            byte[] req = ("""
                %s / HTTP/1.1\r
                Host: localhost:%d\r
                Connection: close\r
                \r
                """.formatted(method, PORT))
                    .getBytes(US_ASCII);

            s.getOutputStream().write(req);
            s.getOutputStream().flush();

            return new String(s.getInputStream().readAllBytes(), US_ASCII);
        }
    }

    private @NotNull APIProxy createApiProxy() {
        APIProxy apiProxy = new APIProxy();
        apiProxy.setPort(PORT);
        apiProxy.setFlow(new ArrayList<>(List.of(
                new AbstractInterceptor() {
                    @Override
                    public Outcome handleRequest(Exchange exc) {
                        requestReachedFlow = true;
                        return Outcome.CONTINUE;
                    }
                },
                new ReturnInterceptor())));
        return apiProxy;
    }
}
