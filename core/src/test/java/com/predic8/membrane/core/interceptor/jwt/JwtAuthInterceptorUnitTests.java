package com.predic8.membrane.core.interceptor.jwt;


import com.predic8.membrane.core.exchange.*;
import org.junit.jupiter.api.*;

public class JwtAuthInterceptorUnitTests {


    Exchange exc;
    JwtAuthInterceptor interceptor;

    @BeforeEach
    void setup() {
        exc = new Exchange(null);
        interceptor = new JwtAuthInterceptor();
    }

    @Test
    void testParseComponents() {

        interceptor.handleJwt(exc,"a.b.c");

        interceptor.handleJwt(exc,"a.b");
        Assertions.assertEquals(400, exc.getResponse().getStatusCode());

    }
}
