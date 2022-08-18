/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http.client;

import java.security.InvalidParameterException;
import java.util.Objects;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.config.spring.BaseLocationApplicationContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@MCElement(name="httpClientConfig")
public class HttpClientConfiguration implements ApplicationContextAware {

	private int maxRetries = 5;
	private ConnectionConfiguration connection = new ConnectionConfiguration();
	private ProxyConfiguration proxy;
	private AuthenticationConfiguration authentication;
	private SSLParser sslParser;
	private String baseLocation;
	private boolean useExperimentalHttp2;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		HttpClientConfiguration that = (HttpClientConfiguration) o;
		return maxRetries == that.maxRetries
				&& useExperimentalHttp2 == that.useExperimentalHttp2
				&& Objects.equals(connection, that.connection)
				&& Objects.equals(proxy, that.proxy)
				&& Objects.equals(authentication, that.authentication)
				&& Objects.equals(sslParser, that.sslParser)
				&& Objects.equals(baseLocation, that.baseLocation);
	}

	@Override
	public int hashCode() {
		return Objects.hash(maxRetries,
				connection,
				proxy,
				authentication,
				sslParser,
				baseLocation,
				useExperimentalHttp2);
	}

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

	/**
	 * @description Determines how often Membrane tries to send a message to a target before it gives up and returns an
	 *              error message to the client.
	 *              All tries to all servers count together. For example if you have 2 targets, and a RoundRobin
	 *              strategy, then the number 5 means it tries, in this order: one, two, one, two, one.
	 *              NOTE: the word "retries" is used incorrectly throughout this project. The current meaning is "tries".
	 *              The first attempt, which is semantically not a "re"-try, counts as one already.
	 * @default 5
	 */
	@MCAttribute
	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public SSLParser getSslParser() {
		return sslParser;
	}

	@MCChildElement(order=4, allowForeign = true)
	public void setSslParser(SSLParser sslParser) {
		this.sslParser = sslParser;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		if (applicationContext instanceof BaseLocationApplicationContext)
			setBaseLocation(((BaseLocationApplicationContext)applicationContext).getBaseLocation());
	}

	public String getBaseLocation() {
		return baseLocation;
	}

	public void setBaseLocation(String baseLocation) {
		this.baseLocation = baseLocation;
	}

	public boolean isUseExperimentalHttp2() {
		return useExperimentalHttp2;
	}

	@MCAttribute
	public void setUseExperimentalHttp2(boolean useExperimentalHttp2) {
		this.useExperimentalHttp2 = useExperimentalHttp2;
	}
}
