package com.predic8.membrane.core.interceptor.balancer;

public class Node {

	private long lastUpTime;
	private String host;
	private int port;
	private boolean isUp = false;
	private int counter;
	
	public Node(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	public boolean equals(Object obj) {
		return obj!=null && obj instanceof Node &&
			   host.equals(((Node)obj).getHost()) &&
			   port == ((Node)obj).getPort();
	}
	
	public long getLastUpTime() {
		return lastUpTime;
	}

	public void setLastUpTime(long lastUpTime) {
		this.lastUpTime = lastUpTime;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isUp() {
		return isUp;
	}

	public void setUp(boolean isUp) {
		this.isUp = isUp;
	}
	
	@Override
	public String toString() {
		return "["+host+":"+port+"]";
	}

	public int getCounter() {
		return counter;
	}

	public void incCounter() {
		counter++;		
	}

}
