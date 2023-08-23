package com.predic8.membrane.core.interceptor.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LazyRateLimitTest {

    int requestLimit = 100;
    LazyRateLimit limiter;

    @BeforeEach
    void setup() {
        limiter = new LazyRateLimit(Duration.ofSeconds(10), requestLimit);
    }

    @Test
    void isRequestLimitReached() {
        IntStream.range(0, limiter.getRequestLimit())
            .parallel()
            .forEach(i -> assertFalse(limiter.isRequestLimitReached("foo")));

        assertTrue(limiter.isRequestLimitReached("foo"));
    }
}