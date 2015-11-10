package com.predic8.membrane.core.interceptor.ratelimit;

import java.util.HashMap;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public class LazyRateLimit extends RateLimitStrategy {

	private DateTime nextCleanup = new DateTime();
	public HashMap<String, IPAddressInfo> ipRequestCounter = new HashMap<String, IPAddressInfo>();

	public LazyRateLimit(Duration requestLimitDuration, int requestLimit) {
		this.requestLimitDuration = requestLimitDuration;
		this.requestLimit = requestLimit;
		incrementNextCleanupTime();
	}

	@Override
	public boolean isRequestLimitReached(String ip) {
		if (DateTime.now().isAfter(nextCleanup)) {
			for (IPAddressInfo info : ipRequestCounter.values()) {
				info.currentRequests.set(0);
			}
			incrementNextCleanupTime();
		}
		addRequestEntry(ip);
		return ipRequestCounter.get(ip).currentRequests.get() > requestLimit;
	}

	private void addRequestEntry(String addr) {
		synchronized (ipRequestCounter) {
			if (!ipRequestCounter.containsKey(addr)) {
				ipRequestCounter.put(addr, new IPAddressInfo());
			}
		}
		ipRequestCounter.get(addr).currentRequests.incrementAndGet();
	}

	private void incrementNextCleanupTime() {
		nextCleanup = DateTime.now().plus(requestLimitDuration);
	}

	@Override
	public DateTime getServiceAvailableAgainTime(String ip) {
		return nextCleanup;
	}

}
