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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.util.*;
import redis.clients.jedis.exceptions.*;

import java.time.*;

/**
 * @description <p>Keeps the request counts of a <code>rateLimiter</code> in Redis, so that several Membrane instances
 * using the same Redis share one limit. Without it, every instance counts on its own.</p>
 * <p>The periods start at multiples of the duration since 1970-01-01 UTC, so all instances agree on when the counts
 * are reset. Keep the clocks of the instances in sync, e.g. with NTP.</p>
 * @yaml
 * <pre><code>
 * components:
 *   redisStore:
 *     redis:
 *       host: localhost
 * ---
 * api:
 *   port: 2000
 *   flow:
 *     - rateLimiter:
 *         requestLimit: 10
 *         requestLimitDuration: PT1M
 *         redisRateLimitStore:
 *           connector: '#/components/redisStore'
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 */
@MCElement(name = "redisRateLimitStore")
public class RedisRateLimitStore implements RateLimitStore {

	private final Clock clock;

	private RedisConnector connector;
	private String prefix = "membrane:ratelimit";

	public RedisRateLimitStore() {
		this(Clock.systemUTC());
	}

	RedisRateLimitStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public RequestCount countRequest(String key, Duration period) {
		final var periodMillis = period.toMillis();
		final var now = clock.millis();
		final var periodStart = now - now % periodMillis;
		final var periodEnd = periodStart + periodMillis;
		try {
			final var count = increment("%s:%d:%d:%s".formatted(prefix, periodMillis, periodStart, key), periodEnd);
			return new RequestCount((int) Math.min(count, Integer.MAX_VALUE), LocalDateTime.ofInstant(Instant.ofEpochMilli(periodEnd), ZoneId.systemDefault()));
		} catch (JedisException e) {
			throw new RateLimitStoreException("Cannot count request in Redis.", e);
		}
	}

	/**
	 * The key contains the start of its period, so every request can set the same absolute expiry. Sending
	 * both commands in one transaction means a key is never left without expiry.
	 */
	long increment(String redisKey, long expireAtMillis) {
		try (var jedis = connector.getJedisWithDb(); var transaction = jedis.multi()) {
			final var count = transaction.incr(redisKey);
			transaction.pexpireAt(redisKey, expireAtMillis);
			transaction.exec();
			return count.get();
		}
	}

	@SuppressWarnings("unused")
	public RedisConnector getConnector() {
		return connector;
	}

	/**
	 * @description Reference to a <code>redis</code> component holding the connection to the Redis server.
	 * @example '#/components/redis'
	 */
	@Required
	@MCAttribute
	public void setConnector(RedisConnector connector) {
		this.connector = connector;
	}

	public String getPrefix() {
		return prefix;
	}

	/**
	 * @description Prefix of the Redis keys. Give limiters that share a Redis but should count separately
	 * different prefixes, and use the same prefix for a limiter on all instances.
	 * @default membrane:ratelimit
	 * @example membrane:ratelimit:orders
	 */
	@MCAttribute
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
}
