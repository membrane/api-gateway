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

import java.time.*;

public abstract class RateLimitStrategy {

	protected java.time.Duration requestLimitDuration;
	protected int requestLimit;

	public java.time.Duration getRequestLimitDuration() {
		return requestLimitDuration;
	}

	public void setRequestLimitDuration(java.time.Duration requestLimitDuration) {
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

	public String getLimitDurationPeriod() {
		return requestLimitDuration.toString();
	}

	/**
	 * @return seconds from now until the deadline, 0 if it has passed
	 */
	public static String secondsUntil(LocalDateTime deadline) {
		return Long.toString(Math.max(0, deadline.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
	}

	/**
	 * Counts a request for the key.
	 *
	 * @return number of requests for the key in the current period, including this one
	 */
	public abstract int countRequest(String key);

	public abstract LocalDateTime getServiceAvailableAgainTime(String key);

	public abstract void updateAfterConfigChange();
}