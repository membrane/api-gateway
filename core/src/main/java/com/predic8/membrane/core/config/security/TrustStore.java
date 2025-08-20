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
 * @description Configuration element for a truststore containing trusted CA certificates.
 *
 * <p>Used by Membrane's TLS components to validate remote certificates
 * presented during SSL/TLS handshakes.</p>
 */
@MCElement(name="truststore")
public class TrustStore extends Store {

	protected String algorithm;
	protected String checkRevocation;

	/**
	 * <p>Equality is based on the base {@link Store} fields plus
	 * the {@code algorithm} and {@code checkRevocation} fields.</p>
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TrustStore other))
			return false;
		return super.equals(obj)
				&& equal(algorithm, other.algorithm)
                && equal(checkRevocation, other.checkRevocation);
	}

	/** Computes a hash code including {@link Store} fields and trust-specific attributes. */
	@Override
	public int hashCode() {
		return java.util.Objects.hash(super.hashCode(), algorithm, checkRevocation);
	}

	/** @return the algorithm. */
	public String getAlgorithm() {
		return algorithm;
	}

	/**
	 * @description Trust manager algorithm used to validate certificate chains.
	 */
	@MCAttribute
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

    /**
     * @return the comma-separated revocation options.
     * Maps to {@link java.security.cert.PKIXRevocationChecker.Option}.
     */
	public String getCheckRevocation() {
		return checkRevocation;
	}

    /**
     * @description Comma-separated PKIX revocation options: ONLY_END_ENTITY, PREFER_CRLS, NO_FALLBACK, SOFT_FAIL.
     * @example ONLY_END_ENTITY,SOFT_FAIL
     * @see java.security.cert.PKIXRevocationChecker.Option
     */
	@MCAttribute
	public void setCheckRevocation(String checkRevocation) {
		this.checkRevocation = checkRevocation;
	}
}
