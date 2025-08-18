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

package com.predic8.membrane.core.config.security;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

import static com.google.common.base.Objects.equal;

/**
 * Configuration element for a keystore holding private keys and certificates.
 */
@MCElement(name="keystore")
public class KeyStore extends Store {

	private String keyPassword;
	private String keyAlias;

	/**
	 * <p>Equality is based on the base {@link Store} fields plus
	 * {@code keyPassword} and {@code keyAlias}.</p>
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof KeyStore other))
			return false;
        return super.equals(obj)
				&& equal(keyPassword, other.keyPassword)
				&& equal(keyAlias, other.keyAlias);
	}

	/** Computes a hash code including {@link Store} fields and key attributes. */
	@Override
	public int hashCode() {
		return java.util.Objects.hash(super.hashCode(), keyPassword, keyAlias);
	}

	/** @return the password protecting the private key inside the keystore. */
	public String getKeyPassword() {
		return keyPassword;
	}

	/**
	 * @description Password used to unlock the private key entry in the keystore.
	 */
	@MCAttribute
	public void setKeyPassword(String keyPassword) {
		this.keyPassword = keyPassword;
	}

	/** @return the alias of the private key entry inside the keystore. */
	public String getKeyAlias() {
		return keyAlias;
	}

	/**
	 * @description The alias identifying which key entry to use from the keystore.
	 */
	@MCAttribute
	public void setKeyAlias(String keyAlias) {
		this.keyAlias = keyAlias;
	}

}
