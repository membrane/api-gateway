/* Copyright 2009, 2012, 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;

public abstract class Store {

	protected String location;
	protected String password;
	protected String type;
	protected String provider;

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Store))
			return false;
		Store other = (Store) obj;
		return Objects.equal(location, other.location)
				&& Objects.equal(password, other.password)
				&& Objects.equal(type, other.type)
				&& Objects.equal(provider, other.provider);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(location, password, type, provider);
	}

	public String getLocation() {
		return location;
	}

	/**
	 * @description A file/resource containing the PKCS#12 keystore (*.p12).
	 * See <a href="https://www.membrane-soa.org/service-proxy-doc/current/configuration/location.htm">here</a> for a description of the format.
	 */
	@MCAttribute
	public void setLocation(String location) {
		this.location = location;
	}

	public String getPassword() {
		return password;
	}

	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

	public String getType() {
		return type;
	}

	@MCAttribute
	public void setType(String type) {
		this.type = type;
	}

	public String getProvider() {
		return provider;
	}

	@MCAttribute
	public void setProvider(String provider) {
		this.provider = provider;
	}
}
