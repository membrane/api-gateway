package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

public class AccessLogServiceTest {

    @Test
    void parseValueSimple() throws URISyntaxException {
        Exchange exc = getExchange(Request.get("/foo").header("foo", "bar").build());

        AdditionalVariable av = new AdditionalVariable();
        av.setExpression("headers.foo");

        assertEquals("bar", AccessLogService.parseValue(exc,av));

    }

    @Test
    void parseValueWithDashes() throws URISyntaxException {
        Exchange exc = getExchange(Request.get("/foo").header("x-foo", "bar").build());

        AdditionalVariable av = new AdditionalVariable();
        av.setExpression("headers['x-foo']");

        assertEquals("bar", AccessLogService.parseValue(exc,av));
    }

    @NotNull
    private static Exchange getExchange(Request build) {
        Exchange exc = new Exchange(null);
        exc.setRequest(build);
        exc.setResponse(Response.ok().build());
        return exc;
    }
}