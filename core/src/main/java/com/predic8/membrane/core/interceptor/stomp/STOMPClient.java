/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.interceptor.stomp;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.ssl.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;

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
	@MCChildElement(allowForeign = true, order=2)
	public void setSslOutboundParser(SSLParser sslParser) {
		this.sslOutboundParser = sslParser;
	}

	@Override
	public void init() {
		super.init();
		connectionManager = new ConnectionManager(connectionConfiguration.getKeepAliveTimeout(), router.getTimerManager());
		if (sslOutboundParser != null)
			sslOutboundProvider = new StaticSSLContext(sslOutboundParser, router.getResolverMap(), router.getBaseLocation());
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
        try {
            return handleRequestInternal(exc);
        } catch (IOException e) {
			ProblemDetails.user(router.isProduction())
					.component(getDisplayName())
					.detail("Error in STOMP client!")
					.exception(e)
					.stacktrace(true)
					.buildAndSetResponse(exc);
			return ABORT;
        }
    }

	public Outcome handleRequestInternal(Exchange exc) throws IOException {
		String login = exc.getRequest().getHeader().getFirstValue("login");
		String passcode = exc.getRequest().getHeader().getFirstValue("passcode");
		String host = exc.getRequest().getHeader().getFirstValue("host");
		String acceptVersion = exc.getRequest().getHeader().getFirstValue("accept-version");

		boolean isStomp1_0 = login != null && passcode != null;
		boolean isStomp1_1orAbove = host != null && acceptVersion != null;

		if (isStomp1_0 || isStomp1_1orAbove) {
			Connection c = connectionManager.getConnection(this.host, port, connectionConfiguration.getLocalAddr(), sslOutboundProvider, connectionConfiguration.getTimeout());
			exc.getRequest().writeSTOMP(c.out, false);
			HttpClient.setupConnectionForwarding(exc, c, "STOMP", getRouter().getStatistics().getStreamPumpStats());
		} else {
			exc.setResponse(Response.badRequest().build());
		}

		return Outcome.RETURN;
	}
}
