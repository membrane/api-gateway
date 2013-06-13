package com.predic8.membrane.core.transport.http.client;

import java.security.InvalidParameterException;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="httpClientConfig")
public class HttpClientConfiguration {
	
	private int maxRetries = 5;
	private ConnectionConfiguration connection = new ConnectionConfiguration();
	private ProxyConfiguration proxy;
	private AuthenticationConfiguration authentication;
	
	public ConnectionConfiguration getConnection() {
		return connection;
	}
	
	@MCChildElement(order=1)
	public void setConnection(ConnectionConfiguration connection) {
		if (connection == null)
			throw new InvalidParameterException("'connection' parameter cannot be null.");
		this.connection = connection;
	}
	
	public ProxyConfiguration getProxy() {
		return proxy;
	}
	
	@MCChildElement(order=2)
	public void setProxy(ProxyConfiguration proxy) {
		this.proxy = proxy;
	}
	
	public AuthenticationConfiguration getAuthentication() {
		return authentication;
	}
	
	@MCChildElement(order=3)
	public void setAuthentication(AuthenticationConfiguration authentication) {
		this.authentication = authentication;
	}
	
	public int getMaxRetries() {
		return maxRetries;
	}
	
	@MCAttribute
	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

}
