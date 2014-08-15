package com.predic8.membrane.core.interceptor.stomp;

import java.net.Inet4Address;

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.ConnectionManager;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.ConnectionConfiguration;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

@MCElement(name="stompClient")
public class STOMPClient extends AbstractInterceptor {
	
	// config
	private int port = 61613;
	private String host = null;
	private ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();
	private SSLParser sslOutboundParser;

	// operational
	private ConnectionManager connectionManager;
	private SSLProvider sslOutboundProvider;

	public int getPort() {
		return port;
	}
	
	/**
	 * @description The port to connect to.
	 * @default 61613
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}
	
	public String getHost() {
		return host;
	}
	
	/**
	 * @description The host (name or IP) to connect to.
	 */
	@Required
	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}
	
	public ConnectionConfiguration getConnectionConfiguration() {
		return connectionConfiguration;
	}
	
	/**
	 * @description Parameters for outbound STOMP connections.
	 */
	@MCChildElement(order=1)
	public void setConnectionConfiguration(ConnectionConfiguration connectionConfiguration) {
		this.connectionConfiguration = connectionConfiguration;
	}

	public SSLParser getSslOutboundParser() {
		return sslOutboundParser;
	}
	
	
	/**
	 * @description Configures outbound SSL (STOMP via SSL).
	 */
	@MCChildElement(order=2)
	public void setSslOutboundParser(SSLParser sslParser) {
		this.sslOutboundParser = sslParser;
	}

	@Override
	public void init() throws Exception {
		connectionManager = new ConnectionManager(connectionConfiguration.getKeepAliveTimeout());
		if (sslOutboundParser != null)
			sslOutboundProvider = new SSLContext(sslOutboundParser, router.getResolverMap(), router.getBaseLocation());
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String login = exc.getRequest().getHeader().getFirstValue("login");
		String passcode = exc.getRequest().getHeader().getFirstValue("passcode");
		String host = exc.getRequest().getHeader().getFirstValue("host");
		String acceptVersion = exc.getRequest().getHeader().getFirstValue("accept-version");

		boolean isStomp1_0 = login != null && passcode != null; 
		boolean isStomp1_1orAbove = host != null && acceptVersion != null;
		
		if (isStomp1_0 || isStomp1_1orAbove) {
			Connection c = connectionManager.getConnection(Inet4Address.getByName(this.host), port, connectionConfiguration.getLocalAddr(), sslOutboundProvider, connectionConfiguration.getTimeout());
			exc.getRequest().writeSTOMP(c.out);
			HttpClient.setupConnectionForwarding(exc, c, "STOMP");
		} else {
			exc.setResponse(Response.badRequest().build());
		}
		
		return Outcome.RETURN;
	}
}
