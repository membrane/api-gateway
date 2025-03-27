package com.predic8.membrane.core.interceptor;

import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.WsTestInterceptor.computeKeyResponse;
import static org.junit.jupiter.api.Assertions.*;

class WsTestInterceptorTest {

    @Test
    public void testKeyResponse() {
        assertEquals("vvtlPs9jLaZ5KqY6wzvtYznMEpQ=",
            computeKeyResponse("+2chusljI/LtPLXb4+gMZg=="));
    }

}