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

package com.predic8.membrane.examples.tests;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.core.interceptor.authentication.session.totp.OtpProvider;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.test.AssertUtils;

public class LoginTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("login")).script("service-proxy").waitForMembrane().start();
		try {
			String form = AssertUtils.getAndAssert200("http://localhost:2000/");
			AssertUtils.assertContains("Username:", form);
			AssertUtils.assertContains("Password:", form);

			form = AssertUtils.postAndAssert(200, "http://localhost:2000/login/",
					new String[] { "Content-Type", "application/x-www-form-urlencoded" },
					"username=john&password=password");
			AssertUtils.assertContains("token:", form);

			String token = new OtpProvider().getNextCode("abcdefghijklmnop", System.currentTimeMillis());

			form = AssertUtils.postAndAssert(200, "http://localhost:2000/login/",
					new String[] { "Content-Type", "application/x-www-form-urlencoded" },
					"token=" + token);

			// successful login?
			AssertUtils.assertContains("This page has moved to", form);

			// access the "protected" page
			form = AssertUtils.getAndAssert200("http://localhost:2000/");
			AssertUtils.assertContains("predic8.com", form);

			// logout
			form = AssertUtils.getAndAssert200("http://localhost:2000/login/logout");
			AssertUtils.assertContains("Username:", form);

		} finally {
			sl.killScript();
		}
	}

}
