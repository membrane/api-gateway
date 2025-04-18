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

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.core.interceptor.authentication.session.totp.OtpProvider;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.test.StringAssertions.assertContains;

public class LoginExampleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "security/login";
	}

	@Test
	public void test() throws Exception {
		try (Process2 ignored =startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
			String form = ha.getAndAssert200("http://localhost:2000/");
			assertContains("Username:", form);
			assertContains("Password:", form);

			form = ha.postAndAssert(200, "http://localhost:2000/login/",
					new String[] { "Content-Type", "application/x-www-form-urlencoded" },
					"username=john&password=password");
			assertContains("token:", form);

			form = ha.postAndAssert(200, "http://localhost:2000/login/",
					new String[] { "Content-Type", APPLICATION_X_WWW_FORM_URLENCODED },
					"token=" + getToken());

			// successful login?
			assertContains("This page has moved to", form);

			// access the "protected" page
			assertContains("predic8.com", ha.getAndAssert200("http://localhost:2000/"));

			// logout
			assertContains("Username:", ha.getAndAssert200("http://localhost:2000/login/logout"));
		}
	}

	private String getToken() {
		return new OtpProvider().getNextCode("abcdefghijklmnop", System.currentTimeMillis());
	}
}