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

package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.transport.ssl.StaticSSLContext;

public abstract class AbstractServiceProxy extends SSLableProxy {

	/**
	 * @description <p>
	 *              The destination where the service proxy will send messages to. Use the target element, if you want
	 *              to send the messages to a static target. If you want to use dynamic destinations have a look at the
	 *              <a href="http://ms.org:8080/esb-doc/configuration/reference/router.htm">content based router</a>.
	 *              </p>
	 */
	@MCElement(name="target", topLevel=false)
	public static class Target {
		private String host;
		private int port = -1;
		private String url;
		private boolean adjustHostHeader = true;

		private SSLParser sslParser;

		public Target() {}

		public Target(String host) {
			setHost(host);
		}

		public Target(String host, int port) {
			setHost(host);
			setPort(port);
		}

		public String getHost() {
			return host;
		}

		/**
		 * @description Host address of the target.
		 * @example localhost, 192.168.1.1
		 */
		@MCAttribute
		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return port;
		}

		/**
		 * @description Port number of the target.
		 * @default 80
		 * @example 8080
		 */
		@MCAttribute
		public void setPort(int port) {
			this.port = port;
		}

		public String getUrl() {
			return url;
		}

		/**
		 * @description Absolute URL of the target. If this is set, <i>host</i> and <i>port</i> will be ignored.
		 * @example http://membrane-soa.org
		 */
		@MCAttribute
		public void setUrl(String url) {
			this.url = url;
		}

		public SSLParser getSslParser() {
			return sslParser;
		}


		/**
		 * @description Configures outbound SSL (HTTPS).
		 */
		@MCChildElement(allowForeign = true)
		public void setSslParser(SSLParser sslParser) {
			this.sslParser = sslParser;
		}

		public boolean isAdjustHostHeader() {
			return adjustHostHeader;
		}

		@MCAttribute
		public void setAdjustHostHeader(boolean adjustHostHeader) {
			this.adjustHostHeader = adjustHostHeader;
		}
	}

	protected Target target = new Target();

	public String getTargetScheme() {
		return getSslOutboundContext() != null ? "https" : "http";
	}

	@Override
	public void init() throws Exception {
		super.init();
		if(target.port == -1)
			target.port = target.getSslParser() != null ? 443 : 80;
		if (target.getSslParser() != null)
			setSslOutboundContext(new StaticSSLContext(target.getSslParser(), router.getResolverMap(), router.getBaseLocation()));
	}

	public String getHost() {
		return ((AbstractRuleKey)key).getHost();
	}

	/**
	 * @description <p>A space separated list of hostnames. If set, Membrane will only consider this rule, if the "Host"
	 *              header of incoming HTTP requests matches one of the hostnames.
	 *              </p>
	 *              <p>
	 *              The asterisk '*' can be used for basic globbing (to match any number, including zero, characters).
	 *              </p>
	 * @default <i>not set</i>
	 * @example predic8.de *.predic8.de
	 */
	@MCAttribute
	public void setHost(String host) {
		((ServiceProxyKey)key).setHost(host);
	}

	public Path getPath() {
		AbstractRuleKey k = (AbstractRuleKey)key;
		if (!k.isUsePathPattern())
			return null;
		return new Path(k.isPathRegExp(), k.getPath());
	}

	/**
	 * @description <p>
	 *              If set, Membrane will only consider this rule, if the path of incoming HTTP requests matches.
	 *              {@link Path} supports starts-with and regex matching.
	 *              </p>
	 *              <p>
	 *              If used in a {@link SOAPProxy}, this causes path rewriting of SOAP requests and in the WSDL to
	 *              automatically be configured.
	 *              </p>
	 */
	@MCChildElement(order=50)
	public void setPath(Path path) {
		AbstractRuleKey k = (AbstractRuleKey)key;
		k.setUsePathPattern(path != null);
		if (path != null) {
			k.setPathRegExp(path.isRegExp());
			k.setPath(path.getValue());
		}
	}

	public String getTargetHost() {
		return target.getHost();
	}

	public int getTargetPort() {
		return target.getPort();
	}

	public String getTargetURL() {
		return target.getUrl();
	}

	@Override
	public boolean isTargetAdjustHostHeader() {
		return target.isAdjustHostHeader();
	}

}
