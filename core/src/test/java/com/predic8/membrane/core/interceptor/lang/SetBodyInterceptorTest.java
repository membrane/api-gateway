package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Response.notImplemented;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple test is enough to test the logic of the interceptor.
 * Complex expressions are tested in the ExchangeExpressionTest, and ...
 */
class SetBodyInterceptorTest {

    private SetBodyInterceptor sbi;
    private Exchange exc;

    @BeforeEach
    void setup() throws URISyntaxException {
        sbi = new SetBodyInterceptor();

        exc = Request.get("/foo").buildExchange();
        exc.setResponse(notImplemented().body("bar").build());
    }

    @Test
    void nullResult() {
        sbi.setValue("null");
        sbi.init(new Router());
        sbi.handleRequest(exc);
        assertEquals("null", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void evalOfSimpleExpression() {
        sbi.setValue("${path}");
        sbi.init(new Router());
        sbi.handleRequest(exc);
        assertEquals("/foo", exc.getRequest().getBodyAsStringDecoded());
    }

        @Test
    void response() {
        sbi.setValue("SC: ${statusCode}");
        sbi.init(new Router());
        sbi.handleRequest(exc);
        assertEquals("SC: 501", exc.getRequest().getBodyAsStringDecoded());
    }
}