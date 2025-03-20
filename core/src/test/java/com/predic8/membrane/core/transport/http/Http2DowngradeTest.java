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
    public void testRFC7540UpgradeIsRemovedFromRequests() throws Exception {
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
