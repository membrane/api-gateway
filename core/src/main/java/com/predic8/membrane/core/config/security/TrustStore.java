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

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name="truststore")
public class TrustStore extends Store {

	protected String algorithm;
	protected String checkRevocation;

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TrustStore))
			return false;
		TrustStore other = (TrustStore) obj;
		return super.equals(obj)
				&& Objects.equal(algorithm, other.algorithm);
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(super.hashCode(), algorithm, checkRevocation);
	}

	public String getAlgorithm() {
		return algorithm;
	}

	@MCAttribute
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public String getCheckRevocation() {
		return checkRevocation;
	}

	@MCAttribute
	public void setCheckRevocation(String checkRevocation) {
		this.checkRevocation = checkRevocation;
	}
}
