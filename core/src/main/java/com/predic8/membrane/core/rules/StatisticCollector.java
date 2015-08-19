/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.rules;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.exchangestore.MemoryExchangeStore;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.transport.http.AbstractHttpHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link StatisticCollector} counts {@link Exchange} objects, tracks the time they took
 * to complete, the number of bytes they sent, and some more numbers.
 *
 * Instances are not thread-safe.
 */
public class StatisticCollector {
	private static Log log = LogFactory.getLog(StatisticCollector.class.getName());

	private final NumberFormat nf = NumberFormat.getInstance(Locale.US);

	private final boolean countErrorExchanges;

	private int totalCount = 0;
	private int goodCount = 0;
	private int errorCount = 0;
	private int minTime = Integer.MAX_VALUE;
	private int maxTime = -1;
	private long totalTime = 0;
	private long totalBytesSent = 0;
	private long totalBytesReceived = 0;

	/**
	 * @param countErrorExchanges whether to count failed Exchange objects. Since
	 * {@link AbstractHttpHandler} counts Exchanges before their state is set to completed
	 * (and {@link Exchange#getStatus()} still returns {@link ExchangeState#FAILED},
	 * we need to be able to count them as successful (and track their statistics). On the
	 * other hand {@link MemoryExchangeStore} needs to count failures as failures,
	 * and does not track their values.
	 */
	public StatisticCollector(boolean countErrorExchanges) {
		this.countErrorExchanges = countErrorExchanges;
		nf.setMaximumFractionDigits(3);
	}

	public void collectFrom(AbstractExchange exc) {
		totalCount++;

		if (exc.getStatus() == ExchangeState.FAILED) {
			errorCount++;
			if (!countErrorExchanges)
				return;
		}

		long timeReqSent = exc.getTimeReqSent();
		if (timeReqSent == 0)
			return; // this Exchange did not reach the HTTPClientInterceptor

		long timeResSent = exc.getTimeResSent();
		if (timeResSent == 0)
			return; // this Exchange is not yet completed

		goodCount++;

		int time = (int) (timeResSent - timeReqSent);
		if (time < minTime)
			minTime = time;
		if (time > maxTime)
			maxTime = time;
		totalTime += time;

		try {
			AbstractBody requestBody = exc.getRequest().getBody();
			totalBytesSent += requestBody.isRead() ? requestBody.getLength() : 0;
			AbstractBody responseBody = exc.getResponse().getBody();
			totalBytesReceived += responseBody.isRead() ? responseBody.getLength() : 0;
		} catch (IOException e) {
			log.warn("", e);
		}
	}

	public void collectFrom(StatisticCollector s) {
		totalCount += s.totalCount;
		goodCount += s.goodCount;
		errorCount += s.errorCount;
		minTime = Math.min(minTime, s.minTime);
		maxTime = Math.max(maxTime, s.maxTime);
		totalTime += s.totalTime;
		totalBytesSent += s.totalBytesSent;
		totalBytesReceived += s.totalBytesReceived;
	}

	public int getCount() {
		return totalCount;
	}

	public int getGoodCount() {
		return goodCount;
	}

	public String getMinTime() {
		return minTime == Integer.MAX_VALUE ? "" : "" + nf.format(minTime) + " ms";
	}

	public String getMaxTime() {
		return maxTime == -1 ? "" : "" + nf.format(maxTime) + " ms";
	}

	public String getAvgTime() {
		return goodCount == 0 ? "" : "" + nf.format(((double)totalTime)/goodCount) + " ms";
	}

	public String getBytesSent() {
		return goodCount == 0 ? "" : "" + nf.format(totalBytesSent);
	}

	public String getBytesReceived() {
		return goodCount == 0 ? "" : "" + nf.format(totalBytesReceived);
	}

	@Override
	public String toString() {
		return "min: " + getMinTime() + "   " +
				"max: " + getMaxTime() + "   " +
				"avg: " + getAvgTime() + "   " +
				"total: " + getCount() + "   " +
				"error: " + getErrorCount();
	}

	public String getErrorCount() {
		return ""+errorCount;
	}

}
