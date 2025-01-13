package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.flow.CallInterceptor.copyHeadersFromResponseToRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallInterceptorTest {

    static Exchange exc;

    @BeforeAll
    static void beforeAll() throws URISyntaxException {
        exc = Request.get("/foo").buildExchange();
    }

    @Test
    void filterHeaders() {
        exc.setResponse(Response.ok()
                .header(TRANSFER_ENCODING, "dummy")
                .header(CONTENT_ENCODING, "dummy")
                .header(SERVER, "dummy")
                .header("X-FOO", "42").build());

        copyHeadersFromResponseToRequest(exc);

        assertEquals("42",exc.getRequest().getHeader().getFirstValue("X-FOO"));
        assertNull(exc.getRequest().getHeader().getFirstValue(TRANSFER_ENCODING));
        assertNull(exc.getRequest().getHeader().getFirstValue(CONTENT_ENCODING));
        assertNull(exc.getRequest().getHeader().getFirstValue(SERVER));
    }
}