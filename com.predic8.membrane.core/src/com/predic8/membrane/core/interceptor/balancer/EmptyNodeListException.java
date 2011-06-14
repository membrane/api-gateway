package com.predic8.membrane.core.interceptor.balancer;

public class EmptyNodeListException extends Exception {
	public EmptyNodeListException() {
		super("Node list empty.");
	}
}
