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

import javax.annotation.concurrent.GuardedBy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts in memory, so every Membrane instance counts on its own. All keys share one period.
 */
public class LazyRateLimit implements RateLimitStore {

	private final Object lock = new Object();
	// The first request starts the first period
	@GuardedBy("lock")
	private LocalDateTime nextCleanup = LocalDateTime.MIN;
	public final ConcurrentHashMap<String, AtomicInteger> requestCounterFromKey = new ConcurrentHashMap<>();

	@Override
	public RequestCount countRequest(String key, Duration period) {
		// Counting under the lock that starts a new period keeps the count and its end in the same period
		synchronized (lock) {
			if (LocalDateTime.now().isAfter(nextCleanup)) {
				requestCounterFromKey.clear();
				nextCleanup = LocalDateTime.now().plus(period);
			}
			return new RequestCount(requestCounterFromKey.computeIfAbsent(key, s -> new AtomicInteger()).incrementAndGet(), nextCleanup);
		}
	}
}
