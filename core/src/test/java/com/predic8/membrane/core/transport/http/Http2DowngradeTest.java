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
package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2DowngradeTest {

    private static HttpRouter router;

    @BeforeAll
    public static void beforeAll() throws IOException {
        router = new HttpRouter();
        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey(3064), null, 0);
        proxy.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                exc.setResponse(Response.ok(exc.getRequest().getHeader().toString()).build());
                return RETURN;
            }
        });
        router.getRules().add(proxy);
        router.start();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        router.stop();
    }

    /**
     * RFC7540 defined a protocol upgrade from HTTP/1.1, but RFC9113 removed support for it.
     * RFC7540 specified that servers not implementing it should just proceed as if it was not present.
     * Membrane therefore removes it. (This test asserts this behaviour.)
     */
    @Test
    void rfc7540UpgradeIsRemovedFromRequests() throws Exception {
        try (HttpClient hc = new HttpClient()) {
            var exc = hc.call(get("http://localhost:3064/")
                    .header("Connection", "Upgrade, HTTP2-Settings")
                    .header("Upgrade", "h2c")
                    .header("HTTP2-Settings", "AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA")
                    .header("X-A", "B")
                    .buildExchange());

            String body = exc.getResponse().getBodyAsStringDecoded();

            assertTrue(body.contains("X-A: B\r\n"));
            assertFalse(body.contains("Connection"));
            assertFalse(body.contains("HTTP2"));
            assertFalse(body.contains("Upgrade"));
        }
    }
}
