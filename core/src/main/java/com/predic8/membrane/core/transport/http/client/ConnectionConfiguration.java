package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="connection", topLevel=false)
public class ConnectionConfiguration {
	
	private long keepAliveTimeout = 30000;
	private long connectTimeout = 10000;
	
	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}
	
	@MCAttribute
	public void setKeepAliveTimeout(long keepAliveTimeout) {
		this.keepAliveTimeout = keepAliveTimeout;
	}
	
	public long getTimeout() {
		return connectTimeout;
	}
	
	public void setTimeout(long timeout) {
		this.connectTimeout = timeout;
	}


}
