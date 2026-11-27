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

package com.predic8.membrane.core.interceptor.ratelimit;

import com.predic8.membrane.core.router.*;

import java.time.*;

/**
 * Keeps the request counts of a {@link RateLimitInterceptor}. A store shared by several Membrane instances,
 * e.g. {@link RedisRateLimitStore}, makes the limit apply to all of them together.
 */
public interface RateLimitStore {

	default void init(Router router) {
	}

	/**
	 * Counts a request for the key.
	 *
	 * @param period length of the period the requests are counted in
	 * @return number of requests for the key in the current period, including this one, and the end of that period
	 * @throws RateLimitStoreException if the store cannot be reached
	 */
	RequestCount countRequest(String key, Duration period);

	/**
	 * @param count   number of requests for the key in the period, including the counted one
	 * @param resetAt end of the period the request was counted in
	 */
	record RequestCount(int count, Instant resetAt) {
	}
}
