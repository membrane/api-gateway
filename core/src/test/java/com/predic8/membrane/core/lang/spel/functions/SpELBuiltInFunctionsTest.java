package com.predic8.membrane.core.lang.spel.functions;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.spel.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static org.junit.jupiter.api.Assertions.*;

class SpELBuiltInFunctionsTest {

    SpELExchangeEvaluationContext ctx;

    @BeforeEach
    void setup() throws URISyntaxException {
        ctx = new SpELExchangeEvaluationContext(post("/foo").xml("<person name='Fritz'/>").buildExchange(), REQUEST);
    }

    @Test
    void xpath() {
        assertEquals("Fritz", SpELBuiltInFunctions.xpath("/person/@name", ctx));
    }
}