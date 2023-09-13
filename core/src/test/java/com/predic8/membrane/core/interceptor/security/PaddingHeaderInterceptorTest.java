package com.predic8.membrane.core.interceptor.security;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaddingHeaderInterceptorTest {

    private static PaddingHeaderInterceptor interceptor;
    private static Exchange exc = new Exchange(null);

    @BeforeAll
    public static void setUp() throws IOException {
        interceptor = new PaddingHeaderInterceptor(1, 1, 1);
    }
    @Test
    void test() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML).body("bar").post("/foo").build());
        interceptor.handleRequest(exc);
        assertTrue(true);
    }

}
