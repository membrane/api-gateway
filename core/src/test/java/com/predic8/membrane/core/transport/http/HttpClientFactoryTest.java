package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.transport.http.client.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientFactoryTest {

    HttpClientFactory f = new HttpClientFactory(null);

    @Test
    void same() {
        assertSame(f.createClient(getConfig(1)), f.createClient(getConfig(1)));
    }

    @Test
    void different() {
        assertNotSame(f.createClient(getConfig(1)), f.createClient(getConfig(2)));
    }

    private static @NotNull HttpClientConfiguration getConfig(int retries) {
        return new HttpClientConfiguration() {{
           setRetryHandler(new RetryHandler() {{
               setRetries(retries);
           }});
        }};
    }
}