/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import java.net.URISyntaxException;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "uriFactory")
public class URIFactory {

	private boolean allowIllegalCharacters;

	public URIFactory() {
		this(false);
	}

	public URIFactory(boolean allowIllegalCharacters) {
		this.allowIllegalCharacters = allowIllegalCharacters;
	}
	
	public boolean isAllowIllegalCharacters() {
		return allowIllegalCharacters;
	}
	
	@MCAttribute
	public void setAllowIllegalCharacters(boolean allowIllegalCharacters) {
		this.allowIllegalCharacters = allowIllegalCharacters;
	}
	
	public URI create(String uri) throws URISyntaxException {
		return new URI(allowIllegalCharacters, uri);
	}
	
	/**
	 * Simply calls {@link #create(String)}, but catches any {@link URISyntaxException} and wraps it in an
	 * {@link IllegalArgumentException}.
	 */
	public URI createWithoutException(String uri) {
		try {
			return new URI(allowIllegalCharacters, uri);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
