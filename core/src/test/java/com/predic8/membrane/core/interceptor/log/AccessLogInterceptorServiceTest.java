package com.predic8.membrane.core.interceptor.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessLogInterceptorServiceTest {
    @Test
    public void test() {
        assertEquals("Test text with \\\" quotes", AccessLogInterceptorService.escapeQuotes("Test text with \" quotes"));
    }
}