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

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.transport.ssl.*;
import org.jetbrains.annotations.*;

public class SSLableProxy extends AbstractProxy {

	private SSLContext sslInboundContext;
	private SSLParser sslInboundParser;

	private SSLContext sslOutboundContext;

	public SSLableProxy() {
	}

	public SSLableProxy(AbstractRuleKey ruleKey) {
		super(ruleKey);
	}

	public void init()  {
		if (sslInboundParser == null)
			return;

		if (sslInboundParser.getAcme() != null) {
			if (!(key instanceof AbstractRuleKey))
				throw new RuntimeException("<acme> only be used inside of <serviceProxy> and similar rules.");
			String[] host = key.getHost().split(" +");
			AcmeSSLContext acmeCtx = (AcmeSSLContext) getSslInboundContext(); // TODO: remove this.
			// getSslInboundContext() of an inactive rule should not be called in the first place.
			if (acmeCtx == null)
				acmeCtx = new AcmeSSLContext(sslInboundParser, host, router.getHttpClientFactory(), router.getTimerManager());
			setSslInboundContext(acmeCtx);
			acmeCtx.init(router.getKubernetesClientFactory(), router.getHttpClientFactory());
			return;
		}
		sslInboundContext = generateSslInboundContext();
	}

	public SSLProvider getSslOutboundContext() {
		return sslOutboundContext;
	}

	protected void setSslOutboundContext(SSLContext sslOutboundContext) {
		this.sslOutboundContext = sslOutboundContext;
	}

	public boolean isOutboundSSL() {
		return sslOutboundContext != null;
	}

	@Override
	public String getProtocol() {
		return isInboundSSL() ? "https" : "http";
	}

	public int getPort() {
		return key.getPort();
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
		return key.getIp();
	}

	/**
	 * @description If present, binds the port only on the specified IP. Useful for hosts with multiple IP addresses.
	 * @default <i>not set</i>
	 * @example 127.0.0.1
	 */
	@MCAttribute
	public void setIp(String ip) {
		key.setIp(ip);
	}

	/**
	 * @description Configures the usage of inbound SSL (HTTPS).
	 */
	@MCChildElement(order = 75, allowForeign = true)
	public void setSslInboundParser(SSLParser sslInboundParser) {
		this.sslInboundParser = sslInboundParser;
	}

	public SSLContext getSslInboundContext() {
		return sslInboundContext;
	}

	protected void setSslInboundContext(SSLContext sslInboundContext) {
		this.sslInboundContext = sslInboundContext;
	}

	public boolean isInboundSSL() {
		return sslInboundContext != null;
	}

	private @NotNull SSLContext generateSslInboundContext() {
		if (sslInboundParser.getKeyGenerator() != null)
			return new GeneratingSSLContext(sslInboundParser, router.getResolverMap(), router.getBaseLocation());
		return new StaticSSLContext(sslInboundParser, router.getResolverMap(), router.getBaseLocation());
	}

}
