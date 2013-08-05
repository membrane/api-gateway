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
package com.predic8.membrane.core.interceptor;

import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

public class IndexInterceptorTest {
	@Test
	public void doit() {
		check(".*");
		check("a.*");
		check("a.+");
		check("a|b(c).*");
		check("a\\Q.|\\E|b.*");
		check("a\\Q.(|\\E|b.*");
		check("a(\\Q.|\\E|b).*", true);
		check("[ab]+", true);
	}
	
	@Test
	public void doit2() {
		check("a(c|b).*");
	}

	/**
	 * Checks whether 'regex' can be fulfilled by the algorithm in
	 * {@link IndexInterceptor} and the fulfillment is actually matched by the
	 * regex.
	 */
	private void check(String regex) {
		check(regex, false);
	}
	
	/**
	 * Same as {@link #check(String)}, but if allowFulfillmentFailure is true,
	 * fulfillment may fail.
	 * If fulfillment succeeds, the result has to be matchable by the regex. 
	 */
	private void check(String regex, boolean allowFulfillmentFailure) {
		String fulfilled = IndexInterceptor.fullfillRegexp(regex);
		if (allowFulfillmentFailure) {
			if (fulfilled == null)
				return;
		}
		Assert.assertTrue(Pattern.matches(regex, fulfilled));
	}

}
