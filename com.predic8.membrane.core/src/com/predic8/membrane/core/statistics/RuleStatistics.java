package com.predic8.membrane.core.statistics;

public class RuleStatistics {

	int min = -1;
	int max = -1;
	float avg = - 1;
	
	long bytesSent = 0;
	long bytesReceived = 0;
	
	int countTotal = 0;
	int countError = 0;
	
	public RuleStatistics() {
		
	}
	
	public int getMin() {
		return min;
	}
	public void setMin(int min) {
		this.min = min;
	}
	public int getMax() {
		return max;
	}
	public void setMax(int max) {
		this.max = max;
	}
	public float getAvg() {
		return avg;
	}
	public void setAvg(float avg) {
		this.avg = avg;
	}
	
	@Override
	public String toString() {
		return "min: " + min + "   " + "max: " + max + "   " + "avg: " + avg + "   " + "total: " + countTotal + "   " + "error: " + countError;
	}

	public long getBytesSent() {
		return bytesSent;
	}

	public void setBytesSent(long bytesSent) {
		this.bytesSent = bytesSent;
	}

	public long getBytesReceived() {
		return bytesReceived;
	}

	public void setBytesReceived(long bytesReceived) {
		this.bytesReceived = bytesReceived;
	}

	public int getCountTotal() {
		return countTotal;
	}

	public void setCountTotal(int countTotal) {
		this.countTotal = countTotal;
	}

	public int getCountError() {
		return countError;
	}

	public void setCountError(int countError) {
		this.countError = countError;
	}
	
	
}
