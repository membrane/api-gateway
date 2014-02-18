/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import java.security.SecureRandom;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class NumericTokenProvider implements TokenProvider {

	private final SecureRandom r = new SecureRandom();

	private long hash(Map<String, String> userAttributes) {
		long hash = 0;
		for (Map.Entry<String, String> entry : userAttributes.entrySet()) {
			hash += entry.getKey().hashCode();
			hash += 3 * entry.getValue().hashCode();
		}
		return hash;
	}

	protected String generateToken(Map<String, String> userAttributes) {
		int t = (int) hash(userAttributes);
		synchronized(r) {
			t = t ^ r.nextInt();
		}
		t = Math.abs(t % 1000000);
		String token = String.format("%06d", t);
		synchronized (userAttributes) {
			userAttributes.put("token", token);
		}
		return token;
	} 

	@Override
	public void verifyToken(Map<String, String> userAttributes, String token) {
		String t1;
		synchronized (userAttributes) {
			t1 = userAttributes.get("token");
		}
		if (t1 == null || !t1.equals(token))
			throw new NoSuchElementException();
	}

}
