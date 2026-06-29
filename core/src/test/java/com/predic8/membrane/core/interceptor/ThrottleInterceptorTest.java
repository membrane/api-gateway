/* Copyright 2010, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.Exchange;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.lang.System.currentTimeMillis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThrottleInterceptorTest {

	volatile boolean success;

	@Test
	public void delaysEveryRequest() throws Exception {
		ThrottleInterceptor i = new ThrottleInterceptor();

		long t = currentTimeMillis();
		i.handleRequest(new Exchange(null));
		assertTrue(currentTimeMillis() - t < 200);

		i.setDelay(300);
		t = currentTimeMillis();
		i.handleRequest(new Exchange(null));
		assertTrue(currentTimeMillis() - t > 200);
	}

	@Test
	public void limitsConcurrentRequests() {
		var i = new ThrottleInterceptor();
		i.setMaxThreads(2);

		assertEquals(CONTINUE, i.handleRequest(new Exchange(null)));
		Exchange held = new Exchange(null);
		assertEquals(CONTINUE, i.handleRequest(held));

		// limit reached, busyDelay = 0 -> immediate rejection
		Exchange rejected = new Exchange(null);
		assertEquals(ABORT, i.handleRequest(rejected));
		assertEquals(503, rejected.getResponse().getStatusCode());

		// freeing a slot admits the next request
		i.handleResponse(held);
		assertEquals(CONTINUE, i.handleRequest(new Exchange(null)));
	}

	@Test
	public void doesNotOverOrDoubleRelease() throws Exception {
		var i = new ThrottleInterceptor();
		i.setMaxThreads(1);

		var holder = new Exchange(null);
		assertEquals(CONTINUE, i.handleRequest(holder));

		// a rejected request never acquired a slot, so completing it must not release one
		Exchange rejected = new Exchange(null);
		assertEquals(ABORT, i.handleRequest(rejected));
		i.handleResponse(rejected);
		i.handleAbort(rejected);
		assertEquals(ABORT, i.handleRequest(new Exchange(null)));

		// releasing the real holder twice frees exactly one slot
		i.handleResponse(holder);
		i.handleResponse(holder);
		assertEquals(CONTINUE, i.handleRequest(new Exchange(null)));
		assertEquals(ABORT, i.handleRequest(new Exchange(null)));
	}

	@Test
	public void waitsUpToBusyDelayThenProceedsWhenSlotFrees() throws Exception {
		final var i = new ThrottleInterceptor();
		i.setMaxThreads(1);
		i.setBusyDelay(30);

		var holder = new Exchange(null);
		assertEquals(CONTINUE, i.handleRequest(holder));

		// no slot frees within busyDelay -> rejected after the wait
		long t = currentTimeMillis();
		assertEquals(ABORT, i.handleRequest(new Exchange(null)));
		assertTrue(currentTimeMillis() - t > 20);

		// a concurrent release lets a waiting request proceed
		success = false;
		var waiter = new Thread(() -> {
			try {
				success = CONTINUE == i.handleRequest(new Exchange(null));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		waiter.start();
		Thread.sleep(10);
		i.handleResponse(holder);
		waiter.join();
		assertTrue(success);
	}

}
