package com.predic8.membrane.core.transport.http;

/**
 * Indicates the state where a connection was closed, but no new request has
 * been started.
 * 
 * Requests may or may not have been completed previously on the same connection
 * (via keep-alive).
 */
public class NoMoreRequestsException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public NoMoreRequestsException() {
	}
	

}
