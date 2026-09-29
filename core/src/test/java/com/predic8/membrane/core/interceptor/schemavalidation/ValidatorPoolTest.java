/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorPoolTest {

    @Test
    void createsInitialObjectsUpFront() {
        var created = new AtomicInteger();
        new ValidatorPool<>(created::incrementAndGet, 3);
        assertEquals(3, created.get());
    }

    @Test
    void createsANewObjectInsteadOfWaitingWhenExhausted() {
        var created = new AtomicInteger();
        var pool = new ValidatorPool<>(created::incrementAndGet, 1);

        var first = pool.borrow();
        var second = pool.borrow();

        assertNotEquals(first, second);
        assertEquals(2, created.get());
    }

    @Test
    void reusesReleasedObjects() {
        var created = new AtomicInteger();
        var pool = new ValidatorPool<>(created::incrementAndGet, 1);

        var first = pool.borrow();
        pool.release(first);

        assertEquals(first, pool.borrow());
        assertEquals(1, created.get());
    }

    @Test
    void discardsReleasedObjectsBeyondTheIdleLimit() {
        var created = new AtomicInteger();
        var pool = new ValidatorPool<>(created::incrementAndGet, 1);

        var first = pool.borrow();
        var second = pool.borrow();
        pool.release(first);
        pool.release(second);

        assertEquals(first, pool.borrow());
        assertEquals(3, pool.borrow());
        assertEquals(3, created.get());
    }

    /**
     * Many threads borrowing and releasing at once: no object may be held by two threads at the
     * same time, and afterwards at most {@code maxIdle} objects are idle.
     */
    @Test
    void neverHandsOutAnObjectTwiceAndCapsIdleUnderContention() throws Exception {
        int maxIdle = 4;
        int threads = 16;
        int iterations = 2_000;
        var created = new AtomicInteger();
        var pool = new ValidatorPool<>(() -> {
            created.incrementAndGet();
            return new Object();
        }, maxIdle);
        Set<Object> held = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = new ArrayList<Future<?>>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        var object = pool.borrow();
                        assertTrue(held.add(object), "object handed to two threads at once");
                        Thread.onSpinWait();
                        assertTrue(held.remove(object));
                        pool.release(object);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures)
                future.get(30, TimeUnit.SECONDS);
        }

        // Drain the pool: every borrow that does not create a new object took an idle one.
        int createdBefore = created.get();
        int idle = 0;
        while (true) {
            pool.borrow();
            if (created.get() != createdBefore)
                break;
            idle++;
        }
        assertTrue(idle <= maxIdle, "%d objects idle, maxIdle is %d".formatted(idle, maxIdle));
    }
}
