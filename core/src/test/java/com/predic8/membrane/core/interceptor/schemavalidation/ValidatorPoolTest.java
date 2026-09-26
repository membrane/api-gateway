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
}
