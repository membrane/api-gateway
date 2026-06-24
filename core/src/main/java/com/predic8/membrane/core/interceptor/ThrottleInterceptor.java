/* Copyright 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @description Throttles incoming traffic by delaying requests and/or capping how many are processed concurrently, to
 * protect a backend from overload. With <code>delay</code> set, every request is held briefly before it continues. With
 * <code>maxThreads</code> set, a request that arrives while the limit is already reached is rejected with 503 once no
 * slot frees up. See the examples under examples/routing-traffic/throttle.
 * @topic 3. Security and Validation
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - throttle:
 *         maxThreads: 10
 *         busyDelay: 3000
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 */
@MCElement(name="throttle")
public class ThrottleInterceptor extends AbstractInterceptor {
	private static final Logger log = LoggerFactory.getLogger(ThrottleInterceptor.class.getName());

	private static final String SLOT_ACQUIRED = "membrane.throttle.slotacquired";

	private long delay = 0;
	private int maxThreads = 0;
	private int busyDelay = 0;

	private Semaphore slots;

	public ThrottleInterceptor() {
		name = "throttle";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if ( delay > 0 ) {
			log.debug("delaying for {} ms",delay);
			sleep(delay);
		}
		if (slots != null && !acquireSlot(exc))
			return ABORT;
		return CONTINUE;
	}

	/**
	 * Reserves a slot atomically, waiting up to busyDelay for one to free up. Marks the exchange so the slot is
	 * released exactly once on response or abort.
	 */
	private boolean acquireSlot(Exchange exc) {
		try {
			if (slots.tryAcquire(busyDelay, MILLISECONDS)) {
				exc.setProperty(SLOT_ACQUIRED, true);
				return true;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		log.info("Max thread limit of {} reached. Server Busy.", maxThreads);
		exc.setResponse(Response.serviceUnavailable("Server busy.").build());
		return false;
	}

	private void releaseSlot(Exchange exc) {
		if (slots == null)
			return;
		if (TRUE.equals(exc.getProperty(SLOT_ACQUIRED))) {
			exc.setProperty(SLOT_ACQUIRED, false);
			slots.release();
		}
	}

	private void sleep(long delay) {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException ignored) {
		}
	}


	@Override
	public Outcome handleResponse(Exchange exc) {
		releaseSlot(exc);
		return CONTINUE;
	}

	@Override
	public void handleAbort(Exchange exc) {
		releaseSlot(exc);
	}

	public long getDelay() {
		return delay;
	}

	/**
	 * @description Milliseconds to hold every request before it continues. <code>0</code> disables the delay.
	 * @default 0
	 * @example 1000
	 */
	@MCAttribute
	public void setDelay(long delay) {
		this.delay = delay;
	}

	public int getMaxThreads() {
		return maxThreads;
	}

	/**
	 * @description Maximum number of requests processed concurrently. A request arriving while the limit is reached
	 * waits up to <code>busyDelay</code> for a slot to free up and, if none does, is rejected with 503. <code>0</code>
	 * means unlimited.
	 * @default 0
	 * @example 5
	 */
	@MCAttribute
	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
		this.slots = maxThreads > 0 ? new Semaphore(maxThreads, true) : null;
	}

	public int getBusyDelay() {
		return busyDelay;
	}

	/**
	 * @description When <code>maxThreads</code> is reached, the maximum time in milliseconds a request waits for a slot
	 * to free up before it is rejected with 503. <code>0</code> rejects immediately.
	 * @default 0
	 * @example 3000
	 */
	@MCAttribute
	public void setBusyDelay(int busyDelay) {
		this.busyDelay = busyDelay;
	}

	@Override
	public String getShortDescription() {
		if (delay > 0 || maxThreads > 0)
			return "Throttles the rate of incoming requests.";
		else
			return "Not configured.";
	}

	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		if (delay > 0)
			sb.append("Delays requests by ").append(String.format("%.1f", delay / 1000.0))
					.append(" seconds.");
		if (maxThreads > 0) {
			if (!sb.isEmpty())
				sb.append(" ");
			sb.append("Only allows ").append(maxThreads)
					.append(" concurrent requests.");
			if (busyDelay > 0)
				sb.append(" The server waits at most ")
						.append(String.format("%.1f", busyDelay / 1000.0))
						.append(" seconds for enough running requests to terminate, ")
						.append("returning an error if the server is still busy after the timeout.");
		}
		return sb.toString();
	}

}
