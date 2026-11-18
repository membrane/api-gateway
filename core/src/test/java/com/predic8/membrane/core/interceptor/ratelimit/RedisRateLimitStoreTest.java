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

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.*;

import java.time.*;
import java.util.*;

import static java.time.Duration.ofMinutes;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisRateLimitStoreTest {

	private static final long NOW = 1_700_000_012_345L;
	private static final long PERIOD_START = 1_699_999_980_000L;
	private static final long PERIOD_END = 1_700_000_040_000L;

	private Jedis jedis;
	private Transaction transaction;
	private RedisRateLimitStore store;

	@BeforeEach
	void setup() {
		jedis = mock(Jedis.class);
		transaction = mock(Transaction.class);
		when(jedis.multi()).thenReturn(transaction);
		final var connector = mock(RedisConnector.class);
		when(connector.getJedisWithDb()).thenReturn(jedis);

		store = new RedisRateLimitStore(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
		store.setConnector(connector);
	}

	@Test
	void countsInKeyOfAlignedPeriodThatExpiresAtItsEnd() {
		final var key = "membrane:ratelimit:60000:%d:%s".formatted(PERIOD_START, sha256Hex("ip:192.0.2.1"));
		final var count = countIs(7L);
		when(transaction.incr(key)).thenReturn(count);

		final var requestCount = store.countRequest("ip:192.0.2.1", ofMinutes(1));

		assertEquals(7, requestCount.count());
		assertEquals(Instant.ofEpochMilli(PERIOD_END), requestCount.resetAt());
		final var inOrder = inOrder(transaction, jedis);
		inOrder.verify(transaction).incr(key);
		inOrder.verify(transaction).pexpireAt(key, PERIOD_END);
		inOrder.verify(transaction).exec();
		inOrder.verify(jedis).close();
	}

	@Test
	void prefixSeparatesLimiters() {
		store.setPrefix("orders");
		final var key = "orders:60000:%d:%s".formatted(PERIOD_START, sha256Hex("key:customer-a"));
		final var count = countIs(1L);
		when(transaction.incr(key)).thenReturn(count);

		assertEquals(1, store.countRequest("key:customer-a", ofMinutes(1)).count());
	}

	@Test
	void keyDoesNotDependOnDefaultLocale() {
		final var defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
		Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("th-TH-u-nu-thai"));
		try {
			final var key = "membrane:ratelimit:60000:" + PERIOD_START + ":" + sha256Hex("ip:192.0.2.1");
			final var count = countIs(1L);
			when(transaction.incr(key)).thenReturn(count);

			assertEquals(1, store.countRequest("ip:192.0.2.1", ofMinutes(1)).count());
		} finally {
			Locale.setDefault(Locale.Category.FORMAT, defaultLocale);
		}
	}

	@Test
	void unreachableRedisIsReported() {
		when(jedis.multi()).thenThrow(new JedisConnectionException("Connection refused"));

		assertThrows(RateLimitStoreException.class, () -> store.countRequest("ip:192.0.2.1", ofMinutes(1)));
	}

	@SuppressWarnings("unchecked")
	private static Response<Long> countIs(long count) {
		final Response<Long> response = mock(Response.class);
		when(response.get()).thenReturn(count);
		return response;
	}
}
