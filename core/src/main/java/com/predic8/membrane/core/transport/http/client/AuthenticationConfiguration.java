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

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="authentication", topLevel=false)
public class AuthenticationConfiguration {

	private String username;
	private String password;

	public String getUsername() {
		return username;
	}

	@Required
	@MCAttribute
	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	@Required
	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

}
