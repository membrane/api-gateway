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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Lock-free pool of objects that are expensive to set up but not thread-safe, such as
 * {@link javax.xml.validation.Validator}s or XSLT {@link javax.xml.transform.Transformer}s.
 * <p>
 * A caller never waits: when no idle object is available, a new one is created, so the pool grows
 * to the peak number of concurrent users. A fixed-size blocking pool instead serialized all
 * request threads on its single lock under load. The factory should therefore be cheap, e.g.
 * create a validator from an already compiled, thread-safe {@link javax.xml.validation.Schema}.
 */
final class ValidatorPool<T> {

    private final Queue<T> idle = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;

    /**
     * @param factory creates a new object when no idle one is available
     * @param initialSize number of objects created up front
     */
    ValidatorPool(Supplier<T> factory, int initialSize) {
        this.factory = factory;
        for (int i = 0; i < initialSize; i++)
            idle.add(factory.get());
    }

    /**
     * Returns an idle object, or a newly created one if none is idle. Hand it back with
     * {@link #release} when done.
     */
    T borrow() {
        var object = idle.poll();
        return object != null ? object : factory.get();
    }

    void release(T object) {
        idle.offer(object);
    }
}
