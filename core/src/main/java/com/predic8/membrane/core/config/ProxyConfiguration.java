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

package com.predic8.membrane.core.config;

import org.apache.commons.codec.binary.Base64;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;

@MCElement(name="proxyConfiguration", group="httpClient", global=false)
public class ProxyConfiguration {

	public static final String ATTRIBUTE_ACTIVE = "active";

	public static final String ATTRIBUTE_AUTHENTICATION = "authentication";

	private boolean active = true;

	private String host;

	private int port;

	private boolean authentication;

	private String username;

	private String password;

	public String getHost() {
		return host;
	}

	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	@MCAttribute
	public void setPort(int proxyPort) {
		this.port = proxyPort;
	}

	public String getPassword() {
		return password;
	}

	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isActive() {
		return active;
	}

	@MCAttribute
	public void setActive(boolean active) {
		this.active = active;
	}

	public String getUsername() {
		return username;
	}

	@MCAttribute
	public void setUsername(String username) {
		this.username = username;
	}

	public boolean isAuthentication() {
		return authentication;
	}

	@MCAttribute
	public void setAuthentication(boolean authentication) {
		this.authentication = authentication;
	}

	/**
	 * The "Basic" authentication scheme defined in RFC 2617 does not properly define how to treat non-ASCII characters.
	 */
	public String getCredentials() {
		StringBuilder buffer = new StringBuilder();
		buffer.append("Basic ");
		byte[] base64UserPass = Base64
				.encodeBase64((username + ":" + password).getBytes(Constants.UTF_8_CHARSET));
		buffer.append(new String(base64UserPass, Constants.UTF_8_CHARSET));
		return buffer.toString();
	}

}
