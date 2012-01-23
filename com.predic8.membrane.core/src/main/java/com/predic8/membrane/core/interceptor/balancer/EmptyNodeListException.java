package com.predic8.membrane.core.interceptor.balancer;

public class EmptyNodeListException extends Exception {
	
	private static final long serialVersionUID = -1239983654002876857L;

	public EmptyNodeListException() {
		super("Node list empty.");
	}
}
