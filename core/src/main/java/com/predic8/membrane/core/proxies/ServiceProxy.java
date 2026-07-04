/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

/**
 * @description A proxy server sits between clients and a backend: it accepts requests, forwards them
 *              to the real server, and returns the response, so clients see only the gateway's
 *              address while the target stays concealed behind it. The <code>serviceProxy</code> is
 *              Membrane's most basic proxy. It accepts HTTP requests on a port, optionally matched by
 *              host, path and method, runs them through the configured flow of plugins, and forwards
 *              them to a target. The other proxies build on it for specific protocols: <code>api</code>
 *              adds REST and OpenAPI support, <code>soapProxy</code> exposes SOAP Web Services from a
 *              WSDL.
 * @topic 1. Proxies and Flow
 * @yaml <pre><code>
 * serviceProxy:
 *   port: 2000
 *   target:
 *     url: https://www.predic8.de
 * </code></pre>
 */
@MCElement(name="serviceProxy")
public class ServiceProxy extends AbstractServiceProxy {

	public ServiceProxy() {
		this.key = new ServiceProxyKey(80);
	}

	public ServiceProxy(ServiceProxyKey ruleKey, String targetHost, int targetPort) {
		this.key = ruleKey;
		this.target.setHost(targetHost);
		this.target.setPort(targetPort);
	}

	public String getMethod() {
		return key.getMethod();
	}

	/**
	 * @description Restricts this proxy to requests whose HTTP method (GET, POST, etc.)
	 *              matches. The asterisk <code>*</code> matches any method.
	 * @default *
	 * @example GET
	 */
	@MCAttribute
	public void setMethod(String method) {
		((ServiceProxyKey)key).setMethod(method);
	}

}
