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


import com.predic8.membrane.annot.MCAttribute;

import static com.google.common.base.Objects.equal;
import static java.util.Objects.hash;

/**
 * Abstract base for keystore/truststore configuration.
 *
 * <p>Holds the common properties required to load a Java {@link java.security.KeyStore KeyStore}:
 * {@linkplain #getLocation() location}, {@linkplain #getPassword() password},
 * {@linkplain #getType() type}, and {@linkplain #getProvider() provider}.
 */
public abstract class Store {

	protected String location;
	protected String password;
	protected String type;
	protected String provider;

	/**
	 * <p>Two stores are equal if and only if their {@code location}, {@code password},
	 * {@code type}, and {@code provider} are equal.
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Store other))
			return false;
        return equal(location, other.location)
				&& equal(password, other.password)
				&& equal(type, other.type)
				&& equal(provider, other.provider);
	}

	/** Computes a hash code based on {@code location}, {@code password}, {@code type}, and {@code provider}. */
	@Override
	public int hashCode() {
		return hash(location, password, type, provider);
	}

	/** @return the resource location of the store. */
	public String getLocation() {
		return location;
	}

	/**
	 * @description A file/resource containing the PKCS#12 keystore (*.p12).
	 */
	@MCAttribute
	public void setLocation(String location) {
		this.location = location;
	}

	/** @return the password protecting the store, or {@code null} if none is set. */
	public String getPassword() {
		return password;
	}

	/**
	 * @description The password used to open the keystore/truststore.
	 */
	@MCAttribute
	public void setPassword(String password) {
		this.password = password;
	}

	/** @return the keystore type (e.g., {@code PKCS12}, {@code JKS}). */
	public String getType() {
		return type;
	}

	/**
	 * @description Keystore type (e.g., <code>PKCS12</code>, <code>JKS</code>).
	 */
	@MCAttribute
	public void setType(String type) {
		this.type = type;
	}

	/** @return the provider used to load the store. */
	public String getProvider() {
		return provider;
	}

	/**
	 * @description Provider to use when loading the keystore.
	 */
	@MCAttribute
	public void setProvider(String provider) {
		this.provider = provider;
	}

}
