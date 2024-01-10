package com.predic8.membrane.core.interceptor;

import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

class InterceptorTest {

    @Test
    void flow() {
         assertTrue(REQUEST.isRequest());
    }

}