package com.predic8.membrane.core.interceptor.ratelimit;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public abstract class RateLimitStrategy {

	protected Duration requestLimitDuration;
	protected int requestLimit;

	public Duration getRequestLimitDuration() {
		return requestLimitDuration;
	}

	public void setRequestLimitDuration(Duration requestLimitDuration) {
		this.requestLimitDuration = requestLimitDuration;
		updateAfterConfigChange();
	}

	public int getRequestLimit() {
		return requestLimit;
	}

	public void setRequestLimit(int requestLimit) {
		this.requestLimit = requestLimit;
		updateAfterConfigChange();
	}

	public abstract boolean isRequestLimitReached(String ip);

	public abstract DateTime getServiceAvailableAgainTime(String ip);

	public abstract void updateAfterConfigChange();
}
