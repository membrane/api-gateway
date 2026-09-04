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
package com.predic8.membrane.core.sslinterceptor;

import com.predic8.membrane.core.router.TestRouter;
import com.predic8.membrane.core.transport.ssl.SSLExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.transport.ssl.TLSError.internal_error;
import static com.predic8.membrane.core.util.RecordingServerTestUtil.freePort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RouterIpResolverInterceptorTest {

    private static final String ROUTER_IP = "127.0.0.1";

    private RouterIpResolverInterceptor interceptor;

    @BeforeEach
    void setUp() throws IOException {
        interceptor = new RouterIpResolverInterceptor();
        interceptor.setRouterIps(ROUTER_IP);
        interceptor.setPort(freePort()); // nothing listens there, so every lookup fails
        interceptor.init(new TestRouter());
    }

    @Test
    @DisplayName("A client that is not one of the routers is passed through untouched")
    void passesThroughWhenTheClientIsNotARouter() throws Exception {
        SSLExchange exc = exchangeFrom("10.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
        assertEquals("10.0.0.1", exc.getRemoteAddrIp());
        assertNull(exc.getError());
    }

    /**
     * SSLProxy turns a rejected connection into a fatal TLS alert built from the error on the
     * exchange, so an interceptor that aborts has to set one.
     */
    @Test
    @DisplayName("A failed lookup aborts and leaves an alert on the exchange")
    void setsAnAlertWhenTheLookupFails() throws Exception {
        SSLExchange exc = exchangeFrom(ROUTER_IP);

        assertEquals(ABORT, interceptor.handleRequest(exc));
        assertEquals(internal_error, exc.getError());
    }

    private static SSLExchange exchangeFrom(String ip) {
        SSLExchange exc = new SSLExchange();
        exc.setRemoteAddrIp(ip);
        exc.setRemotePort(45678);
        return exc;
    }
}
