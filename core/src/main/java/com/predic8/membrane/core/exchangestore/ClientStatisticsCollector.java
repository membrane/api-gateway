/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchangestore;

import com.predic8.membrane.core.exchange.AbstractExchange;

public class ClientStatisticsCollector implements ClientStatistics {
	
	private int count;
	private long minDuration = Long.MAX_VALUE;
	private long maxDuration = Long.MIN_VALUE;
	private long total;
	private String client;
	
	public ClientStatisticsCollector(String client) {
	
		this.client = client;
	}

	public void collect(AbstractExchange exc) {
		if (getDuration(exc) < minDuration) {
			minDuration = getDuration(exc);
		} if (getDuration(exc) > maxDuration) {
			maxDuration = getDuration(exc);
		}
		total += getDuration(exc);
		count ++;
	}
	
	@Override
	public int getCount() {
		return count;
	}


	@Override
	public String getClient() {
		return client;
	}

	@Override
	public long getMinDuration() {
		return minDuration;
	}

	@Override
	public long getMaxDuration() {
		return maxDuration;
	}

	@Override
	public long getAvgDuration() {
		return total/count;
	}

	private long getDuration(AbstractExchange exc) {
		return exc.getTimeResReceived() - exc.getTimeReqSent();
	}
}
