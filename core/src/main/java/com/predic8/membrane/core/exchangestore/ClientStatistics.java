package com.predic8.membrane.core.exchangestore;

public interface ClientStatistics {

	public int getCount();

	public String getClient();

	public long getMinDuration();

	public long getMaxDuration();

	public long getAvgDuration();

}