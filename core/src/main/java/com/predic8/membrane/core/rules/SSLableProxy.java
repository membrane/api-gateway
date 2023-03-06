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

import com.predic8.membrane.core.transport.ssl.StaticSSLContext;
import com.predic8.membrane.core.transport.ssl.GeneratingSSLContext;
import org.apache.commons.lang3.StringUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.ssl.SSLContext;
import com.predic8.membrane.core.transport.ssl.SSLProvider;

public abstract class SSLableProxy extends AbstractProxy {
	private SSLContext sslOutboundContext;

	@Override
	public SSLProvider getSslOutboundContext() {
		return sslOutboundContext;
	}

	protected void setSslOutboundContext(SSLContext sslOutboundContext) {
		this.sslOutboundContext = sslOutboundContext;
	}


	@Override
	public String getName() {
		return StringUtils.defaultIfEmpty(name, getKey().toString());
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

}
