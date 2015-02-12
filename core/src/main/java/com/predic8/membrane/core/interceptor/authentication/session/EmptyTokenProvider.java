/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.Map;
import java.util.NoSuchElementException;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;

@MCElement(name="emptyTokenProvider", topLevel=false)
public class EmptyTokenProvider implements TokenProvider {

	@Override
	public void init(Router router) {
		// does nothing
	}
	
	@Override
	public void requestToken(Map<String, String> userAttributes) {
		// does nothing
	}

	@Override
	public void verifyToken(Map<String, String> userAttributes, String token) {
		if (token.length() == 0)
			throw new NoSuchElementException();
	}

}
