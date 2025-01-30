package com.predic8.membrane.core.proxies;

import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.flow.invocation.FlowTestInterceptors.*;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.*;

class AbstractProxyTest {

    private static AbstractProxy proxy;

    @BeforeAll
    static void beforeAll() {
        proxy = new AbstractProxy() {{
           interceptors = List.of(ECHO, RETURN, ABORT);
        }};
    }

    @Test
    void getFirstInterceptorOfTypeTest() {
        assertEquals( Optional.of(RETURN), proxy.getFirstInterceptorOfType(RETURN.getClass()));
    }

    @Test
    void getFirstInterceptorOfTypeNotFound() {
        assertEquals( empty(), proxy.getFirstInterceptorOfType(EXCEPTION.getClass()));
    }
}