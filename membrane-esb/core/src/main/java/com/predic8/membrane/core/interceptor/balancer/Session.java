package com.predic8.membrane.core.interceptor.balancer;

public class Session {
	Node node;
	long lastUsed;
	String id;
	
	Session(String id, Node node) {
		this.id = id;
		this.node = node;
		lastUsed = System.currentTimeMillis();
	}
	
	public void used() {
		lastUsed = System.currentTimeMillis();
	}

	public Node getNode() {
		return node;
	}

	public long getLastUsed() {
		return lastUsed;
	}

	public String getId() {
		return id;
	}
	
	
}
