package com.predic8.membrane.core.interceptor.ratelimit;

import java.util.ArrayDeque;
import java.util.HashMap;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public class PreciseRateLimit extends RateLimitStrategy {

	private DateTime serviceAvailableAgainDateTime = new DateTime();
	public HashMap<String, ArrayDeque<DateTime>> ipRequestTimes = new HashMap<String, ArrayDeque<DateTime>>();

	public PreciseRateLimit(Duration requestLimitDuration, int requestLimit) {
		this.requestLimitDuration = requestLimitDuration;
		this.requestLimit = requestLimit;
	}

	@Override
	public boolean isRequestLimitReached(String ip) {
		addRequestEntry(ip);
		doIPAddressInfoRequestCleanUp(ip);
		return ipRequestTimes.get(ip).size() > requestLimit;
	}

	private void doIPAddressInfoRequestCleanUp(String ip) {
		ArrayDeque<DateTime> info = ipRequestTimes.get(ip);
		synchronized (info) {
			while (info.size() > 0) {
				DateTime time = info.peekFirst();
				DateTime timeWithAddedDuration = time.plus(requestLimitDuration);
				if (timeWithAddedDuration.isAfterNow()) {
					serviceAvailableAgainDateTime = timeWithAddedDuration;
					break;
				}
				info.pop();
			}
		}
	}

	private synchronized void addRequestEntry(String addr) {
		if (!ipRequestTimes.containsKey(addr)) {
			ipRequestTimes.put(addr, new ArrayDeque<DateTime>());
		}
		ipRequestTimes.get(addr).addLast(DateTime.now());
	}

	@Override
	public DateTime getServiceAvailableAgainTime(String ip) {
		return serviceAvailableAgainDateTime;

	}

	@Override
	public void updateAfterConfigChange() {
	}

}
