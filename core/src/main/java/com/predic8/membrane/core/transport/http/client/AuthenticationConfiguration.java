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

import com.predic8.membrane.annot.*;

import java.util.*;

/**
 * @description Configuration for basic HTTP authentication.
 *              This element can be used to configure credentials for outbound requests requiring HTTP Basic Auth.
 *              Typically used within &lt;httpClientConfig&gt;.
 *
 *              XML Example:
 *              &lt;authentication username="user" password="secret"/&gt;
 *
 *              YAML (experimental):
 *              authentication:
 *                username: user
 *                password: secret
 *
 * @topic 4. Transports and Clients
 */
@MCElement(name="authentication", topLevel=false)
public class AuthenticationConfiguration {

	private String username;
	private String password;

	public String getUsername() {
		return username;
	}

	/**
	 * @description Username used for HTTP Basic Authentication.
	 * @required
	 * @example user
	 */
	@Required
	@MCAttribute
	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	/**
	 * @description Password used for HTTP Basic Authentication.
	 * @required
	 * @example secret
	 */
	@Required
	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AuthenticationConfiguration that = (AuthenticationConfiguration) o;
		return Objects.equals(username, that.username) && Objects.equals(password, that.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(username, password);
	}
}
