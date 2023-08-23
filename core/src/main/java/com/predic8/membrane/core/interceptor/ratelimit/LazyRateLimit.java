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
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LazyRateLimit extends RateLimitStrategy {

	private final Object lock = new Object();
	@GuardedBy("lock")
	private java.time.LocalDateTime nextCleanup = LocalDateTime.now();
	public final ConcurrentHashMap<String, AtomicInteger> requestCounterFromKey = new ConcurrentHashMap<>();

	public LazyRateLimit(java.time.Duration requestLimitDuration, int requestLimit) {
		this.requestLimitDuration = requestLimitDuration;
		this.requestLimit = requestLimit;
		incrementNextCleanupTime();
	}

	@Override
	public boolean isRequestLimitReached(String key) {
		synchronized (lock) {
			if (java.time.LocalDateTime.now().isAfter(nextCleanup)) {
				requestCounterFromKey.clear();
				incrementNextCleanupTime();
			}
		}

		addRequestEntry(key);
		return requestCounterFromKey.get(key).get() > requestLimit;
	}

	private void addRequestEntry(String addr) {
		requestCounterFromKey.computeIfAbsent(addr, s -> new AtomicInteger());
		requestCounterFromKey.get(addr).incrementAndGet();
	}

	private void incrementNextCleanupTime() {
		synchronized (lock) {
			nextCleanup = java.time.LocalDateTime.now().plus(requestLimitDuration);
		}
	}

	@Override
	public LocalDateTime getServiceAvailableAgainTime(String key) {
		return nextCleanup;
	}

	@Override
	public void updateAfterConfigChange() {
		requestCounterFromKey.clear();
		incrementNextCleanupTime();
	}
}
