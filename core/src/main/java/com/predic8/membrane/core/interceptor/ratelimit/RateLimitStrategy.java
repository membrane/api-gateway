package com.predic8.membrane.core.interceptor.ratelimit;

import org.joda.time.DateTime;
import org.joda.time.Duration;

public abstract class RateLimitStrategy {

	protected Duration requestLimitDuration;
	protected int requestLimit;

	public abstract boolean isRequestLimitReached(String ip);

	public abstract DateTime getServiceAvailableAgainTime(String ip);
}
