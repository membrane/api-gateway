package com.predic8.membrane.core.transport.http.client;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientConfigurationTest {

    HttpClientConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new HttpClientConfiguration();
    }

    @Test
    void maxRetries() {
        assertEquals(5,  configuration.getRetryHandler().getRetries());

        RetryHandler rh = new RetryHandler();
        configuration.setRetryHandler(rh);
        assertEquals(5, configuration.getMaxRetries());

        rh.setRetries(10);
        assertEquals(10, configuration.getRetryHandler().getRetries());
    }

}