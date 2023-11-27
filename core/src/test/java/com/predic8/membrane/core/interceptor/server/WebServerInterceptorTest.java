package com.predic8.membrane.core.interceptor.server;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerInterceptorTest {

    WebServerInterceptor ws;
    Exchange exc;
    Router r;

    @BeforeEach
    void init() {
        r = new Router();

        ws = new WebServerInterceptor(r) {{
            setDocBase(Objects.requireNonNull(this.getClass().getResource("/html/")).toString());
        }};

        exc = new Exchange(null) {{
            setOriginalRequestUri("/");
            setDestinations(new ArrayList<>() {{
                add("/");
            }});
        }};
    }

    @Test
    void noIndex() throws Exception {
        ws.setGenerateIndex(false);
        ws.handleRequest(exc);
        // No index file is set, and no index page is generated, so throw not found.
        assertEquals(404, exc.getResponse().getStatusCode());
    }

    @Test
    void generateIndex() throws Exception {
        ws.setGenerateIndex(true);
        ws.handleRequest(exc);
        // No index file is set, but index page is being generated. Body lists the page.html resource.
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("page.html"));
    }
}