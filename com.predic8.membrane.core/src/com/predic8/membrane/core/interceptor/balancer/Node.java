package com.predic8.membrane.core.interceptor.balancer;

import java.util.Hashtable;
import java.util.Map;

public class Node {

	private long lastUpTime;
	private String host;
	private int port;
	private boolean isUp = false;
	private int counter;
	private int threads;
	
	private Map<Integer, Integer> statusCodes = new Hashtable<Integer, Integer>();  
	
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
	
	public int getLost() {
		int received = 0;
		for ( int i : statusCodes.values() ) {
			received += i;
		}
		return counter - received;
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
		if (!isUp) threads = 0;
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

	public void clearCounter() {
		counter = 0;	
		statusCodes.clear();
	}

	public void addStatusCode(int code) {
		if ( !statusCodes.containsKey(code) ) {
			statusCodes.put(code, 0);
		}
		statusCodes.put(code, statusCodes.get(code) + 1 );
	}
	
	public void addThread() {
		if (!isUp) return;
		threads++;		
	}

	public void removeThread() {
		if (!isUp) return;
		threads--;	
	}

	public int getThreads() {
		return threads;
	}

	public Map<Integer, Integer> getStatusCodes() {
		return statusCodes;
	}

}
