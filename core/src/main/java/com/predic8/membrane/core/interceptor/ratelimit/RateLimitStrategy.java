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

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.*;

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

	public String getLimitDurationPeriod() {
		return PeriodFormat.getDefault().print(requestLimitDuration.toPeriod());
	}

	public String getLimitReset(String ip) {
		return Long.toString(getServiceAvailableAgainTime(ip).getMillis());
	}

	public abstract boolean isRequestLimitReached(String ip);

	public abstract DateTime getServiceAvailableAgainTime(String ip);

	public abstract void updateAfterConfigChange();
}
