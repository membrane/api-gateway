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
