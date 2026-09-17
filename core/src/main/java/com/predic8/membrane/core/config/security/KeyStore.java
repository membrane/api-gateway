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
 * @description Loads a private key and its certificate chain from a keystore file, to be
 * presented as this side's identity during a TLS handshake (or, inside <code>wsSecurity</code>,
 * to sign a message). See <tt>tutorials/web-services-security/50-Sign-And-Validate-Body.yaml</tt>.
 * <pre><code>
 * keystore:
 *   location: &lt;file&gt;
 *   [ password: &lt;password&gt; ]        # default: keyPassword
 *   [ keyPassword: &lt;password&gt; ]     # default: changeit
 *   [ keyAlias: &lt;alias&gt; ]           # default: the keystore's first key entry
 *   [ type: PKCS12 | JKS ]           # default: PKCS12
 *   [ provider: &lt;name&gt; ]
 * </code></pre>
 * @yaml <pre><code>
 * wsSecurity:
 *   keystore:
 *     location: signer.p12
 *     password: secret
 *     keyAlias: signer
 *   secure:
 *     - signature:
 *         references:
 *           - by: BODY
 * </code></pre>
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
	 * @description Password unlocking the private key entry inside the keystore. Also used to
	 * open the keystore file itself when <code>password</code> is not set. In YAML, this can be a
	 * SpEL expression reading an environment variable instead of a literal value, e.g.
	 * <tt>"#{env.KEYSTORE_KEY_PASSWORD}"</tt>, so the password itself need not be checked into
	 * version control.
     * @default changeit
     * @example abc123
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
	 * @description Alias of the key entry to use from the keystore, for a keystore holding more
	 * than one.
	 * @default the keystore's first key entry
	 */
	@MCAttribute
	public void setKeyAlias(String keyAlias) {
		this.keyAlias = keyAlias;
	}

}
