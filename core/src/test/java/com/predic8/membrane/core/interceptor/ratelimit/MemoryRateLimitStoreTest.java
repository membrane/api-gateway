/* Copyright 2015 predic8 GmbH, www.predic8.com

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
import java.time.Instant;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MemoryRateLimitStoreTest {

    final int requestLimit = 100;
    final Duration period = Duration.ofSeconds(10);
    MemoryRateLimitStore limiter;

    @BeforeEach
    void setup() {
        limiter = new MemoryRateLimitStore();
    }

    @Test
    void countRequest() {
        final var counts = IntStream.range(0, requestLimit)
            .parallel()
            .map(i -> limiter.countRequest("foo", period).count())
            .sorted()
            .toArray();

        assertArrayEquals(IntStream.rangeClosed(1, requestLimit).toArray(), counts);
        assertEquals(requestLimit + 1, limiter.countRequest("foo", period).count());
        assertEquals(1, limiter.countRequest("bar", period).count());
    }

    @Test
    void countRequestReportsEndOfPeriod() {
        final var before = Instant.now();
        final var resetAt = limiter.countRequest("foo", period).resetAt();

        assertTrue(resetAt.isAfter(before));
        assertFalse(resetAt.isAfter(Instant.now().plusSeconds(10)));
    }
}
