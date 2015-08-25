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

package com.predic8.membrane.core.rules;

import org.apache.commons.lang.StringUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public abstract class SSLableProxy extends AbstractProxy {
	private SSLParser sslInboundParser;
	private SSLContext sslInboundContext, sslOutboundContext;

	@Override
	public SSLContext getSslInboundContext() {
		return sslInboundContext;
	}

	protected void setSslInboundContext(SSLContext sslInboundContext) {
		this.sslInboundContext = sslInboundContext;
	}

	@Override
	public SSLProvider getSslOutboundContext() {
		return sslOutboundContext;
	}

	protected void setSslOutboundContext(SSLContext sslOutboundContext) {
		this.sslOutboundContext = sslOutboundContext;
	}

	public SSLParser getSslInboundParser() {
		return sslInboundParser;
	}

	/**
	 * @description Configures the usage of inbound SSL (HTTPS).
	 */
	@MCChildElement(order=75)
	public void setSslInboundParser(SSLParser sslInboundParser) {
		this.sslInboundParser = sslInboundParser;
	}

	@Override
	public void init() throws Exception {
		if (sslInboundParser != null)
			setSslInboundContext(new SSLContext(sslInboundParser, router.getResolverMap(), router.getBaseLocation()));
	}

	@Override
	public String getName() {
		return StringUtils.defaultIfEmpty(name, getKey().toString());
	}

	public int getPort() {
		return ((AbstractRuleKey)key).getPort();
	}

	/**
	 * @description The port Membrane listens on for incoming connections.
	 * @default 80
	 * @example 8080
	 */
	@MCAttribute
	public void setPort(int port) {
		((AbstractRuleKey)key).setPort(port);
	}

	public String getIp() {
		return ((AbstractRuleKey)key).getIp();
	}

	/**
	 * @description If present, binds the port only on the specified IP. Useful for hosts with multiple IP addresses.
	 * @default <i>not set</i>
	 * @example 127.0.0.1
	 */
	@MCAttribute
	public void setIp(String ip) {
		((AbstractRuleKey)key).setIp(ip);
	}

}
