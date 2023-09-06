/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
