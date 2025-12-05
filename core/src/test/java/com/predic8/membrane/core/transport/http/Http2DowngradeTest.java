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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.transport.http.client.protocol.Http2ProtocolHandler.*;
import static org.junit.jupiter.api.Assertions.*;

public class Http2DowngradeTest {

    private static HttpRouter router;

    @BeforeAll
    public static void beforeAll() {
        router = new HttpRouter();
        ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey(3064), null, 0);
        proxy.getFlow().add(new AbstractInterceptor() {
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
    public static void afterAll() {
        router.stop();
    }

    /**
     * RFC7540 defined a protocol upgrade from HTTP/1.1, but RFC9113 removed support for it.
     * RFC7540 specified that servers not implementing it should just proceed as if it was not present.
     * Membrane therefore removes it. (This test asserts this behaviour.)
     */
    @Test
    void rfc7540UpgradeIsRemovedFromRequests() throws Exception {
        try (var hc = new HttpClient()) {
            var exc = get("http://localhost:3064/")
                    .header(CONNECTION, "Upgrade, HTTP2-Settings")
                    .header(UPGRADE, HTTP2_CLEAR_PROTOCOL)
                    .header("HTTP2-Settings", "AAEAAEAAAAIAAAABAAMAAABkAAQBAAAAAAUAAEAA")
                    .header("X-A", "B")
                    .buildExchange();
            hc.call(exc);

            String body = exc.getResponse().getBodyAsStringDecoded();

            assertTrue(body.contains("X-A: B\r\n"));
            assertFalse(body.contains("Connection"));
            assertFalse(body.contains("HTTP2"));
            assertFalse(body.contains("Upgrade"));
        }
    }
}
