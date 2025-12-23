package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static org.junit.jupiter.api.Assertions.*;

class GroovyBuiltInFunctionsTest {

    GroovyBuiltInFunctions functions;

    @BeforeEach
    void setUp() throws URISyntaxException {
        functions = new GroovyBuiltInFunctions(post("/foo").xml("<person name='Fritz'/>").buildExchange(), REQUEST );
    }

    @Test
    void xpath() {
        assertEquals("Fritz", functions.xpath("/person/@name"));
    }
}