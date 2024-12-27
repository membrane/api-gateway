package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EchoInterceptorTest {

    @Test
    void testEcho() throws Exception {
        Exchange exc = Request
                .post("/foo")
                .header("X-Foo","bar")
                .header("Content-Type", TEXT_PLAIN)
                .body("Content").buildExchange();

        EchoInterceptor ei = new EchoInterceptor();
        Outcome outcome = ei.handleRequest(exc);

        assertEquals(Outcome.RETURN, outcome);
        assertEquals(200, exc.getResponse().getStatusCode());
        assertEquals("bar", exc.getResponse().getHeader().getFirstValue("X-Foo"));
        assertEquals(TEXT_PLAIN, exc.getResponse().getHeader().getContentType());
        assertEquals("Content", exc.getResponse().getBodyAsStringDecoded());
    }

}