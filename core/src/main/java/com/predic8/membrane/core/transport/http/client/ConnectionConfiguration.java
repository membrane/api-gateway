package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="connection", topLevel=false)
public class ConnectionConfiguration {
	
	private long keepAliveTimeout = 4000;
	private int connectTimeout = 10000;
	
	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}
	
	/**
	 * @description Time in milliseconds after which an open connection to the server is not reused. Be sure to set it to a smaller value than the KeepAlive
					directive on your server. Note that the a "Keep-Alive" header in the response always takes precedence. 
	 * @default 4000
	 * @example 30000
	 */
	@MCAttribute
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}
	
	public int getTimeout() {
		return connectTimeout;
	}
	
	/**
	 * @description Socket timeout (connect, read, etc.) in milliseconds.
	 * @default 10000
	 */
	@MCAttribute
	public void setTimeout(int timeout) {
		this.connectTimeout = timeout;
	}


}
