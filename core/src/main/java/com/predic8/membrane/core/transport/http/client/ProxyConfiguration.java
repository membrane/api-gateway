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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.security.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.codec.binary.Base64.*;

/**
 * @description Configuration for an outbound HTTP proxy.
 *              Defines the address, optional authentication credentials, and TLS configuration for connecting to a proxy.
 *              Can be used as a child element in &lt;httpClientConfig&gt; to route client connections through a proxy server.
 *
 *              XML Example:
 *              &lt;proxy host="proxy.example.com" port="3128" authentication="true" username="user" password="secret"&gt;
 *                  &lt;ssl keystoreLocation="classpath:proxy-client.jks" keystorePassword="changeit"/&gt;
 *              &lt;/proxy&gt;
 *
 *              YAML (experimental):
 *              proxy:
 *                host: proxy.example.com
 *                port: 3128
 *                authentication: true
 *                username: user
 *                password: secret
 *                ssl:
 *                  keystoreLocation: classpath:proxy-client.jks
 *                  keystorePassword: changeit
 *
 * @topic 4. Transports and Clients
 */
@MCElement(name="proxy", topLevel=false, id="proxy-configuration")
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
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	public String getCredentials() {
		return  "Basic " + new String(encodeBase64((username + ":" + password).getBytes(UTF_8)), UTF_8);
	}

}
