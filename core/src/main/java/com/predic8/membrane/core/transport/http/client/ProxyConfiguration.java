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

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.security.SSLParser;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.Objects;

import static com.predic8.membrane.core.util.security.BasicAuthenticationUtil.createAuthorizationHeader;

/**
 * @description <p>Configuration for an outbound HTTP proxy used by the HTTP client.
 *              </p>
 *              <p>Defines the proxy endpoint, optional authentication, and TLS settings.
 *              When configured, all outgoing requests are routed through the proxy.
 *              </p>
 *              <p>
 *              This element is typically used as a child of <code>httpClientConfig</code>.
 *              </p>
 *
 * @yaml <pre><code>
 * configuration:
 *   httpClientConfig:
 *     proxy:
 *       host: localhost
 *       port: 3128
 *       authentication: true
 *       username: alice
 *       password: secret
 * </code></pre>
 */
@MCElement(name="proxy", component =false, id="proxy-configuration")
public class ProxyConfiguration {

	private String host;

	private int port;

	private boolean authentication;

	private String username;

	private String password;

	private SSLParser sslParser;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ProxyConfiguration that = (ProxyConfiguration) o;
		return port == that.port
				&& authentication == that.authentication
				&& Objects.equals(host, that.host)
				&& Objects.equals(username, that.username)
				&& Objects.equals(password, that.password)
				&& Objects.equals(sslParser, that.sslParser);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port, authentication, username, password, sslParser);
	}

	public String getHost() {
		return host;
	}

	/**
	 * @description The hostname or IP address of the proxy server.
	 *              Required for proxy usage.
	 * @example proxy.example.com
	 */
	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * @description TCP port on which the proxy server is listening.
	 * @default 0
	 * @example 3128
	 */
	@MCAttribute
	public void setPort(int proxyPort) {
		this.port = proxyPort;
	}

	public String getPassword() {
		return password;
	}

	/**
	 * @description Password for authenticating with the proxy server.
	 *              Only used when authentication="true".
	 * @default (not set)
	 * @example secret
	 */
	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

	public String getUsername() {
		return username;
	}

	/**
	 * @description Username for authenticating with the proxy server.
	 *              Only used when authentication="true".
	 * @default (not set)
	 * @example user
	 */
	@MCAttribute
	public void setUsername(String username) {
		this.username = username;
	}

	public boolean isAuthentication() {
		return authentication;
	}

	/**
	 * @description Whether to send a Basic Authentication header with proxy requests.
	 *              If set to true, &lt;username&gt; and &lt;password&gt; must be provided.
	 * @default false
	 */
	@MCAttribute
	public void setAuthentication(boolean authentication) {
		this.authentication = authentication;
	}

	public SSLParser getSslParser() {
		return sslParser;
	}

	/**
	 * @description SSL configuration for connecting securely to HTTPS proxy servers.
	 *              This is used for TLS-encrypted proxy tunnels.
	 */
	@MCChildElement
	public void setSslParser(SSLParser sslParser) {
		this.sslParser = sslParser;
	}

	/**
	 * Rejects a proxy that cannot be used, instead of failing on the first request that is routed
	 * through it.
	 *
	 * @throws ConfigurationException if the host is missing, or if authentication is switched on
	 *                                without credentials
	 */
	public void validate() {
		if (host == null || host.isBlank())
			throw new ConfigurationException("The proxy needs a host, e.g. host=\"proxy.example.com\".");

		if (!authentication)
			return;

		if (username == null || password == null)
			throw new ConfigurationException(("The proxy %s:%d has authentication=\"true\", so it needs a username "
					+ "and a password. Set both, or remove authentication.").formatted(host, port));
	}

	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	public String getCredentials() {
		return createAuthorizationHeader(username, password);
	}

}
