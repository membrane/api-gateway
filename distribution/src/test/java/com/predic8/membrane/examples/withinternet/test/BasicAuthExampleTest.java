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

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

public class BasicAuthExampleTest extends AbstractSampleMembraneStartStopTestcase {

	public static final String CUSTOMER_HOST_LOCAL_STATIC = "http://localhost:2000/";
	public static final String CUSTOMER_HOST_LOCAL_FILE = "http://localhost:3000/";

	@Override
	protected String getExampleDirName() {
		return "security/basic-auth/simple";
	}

	@Test
	public void testStaticUDProvider() throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			ha.disableHTTPAuthentication();
			ha.getAndAssert(401, CUSTOMER_HOST_LOCAL_STATIC);

			ha.setupHTTPAuthentication("localhost", 2000, "alice", "membrane");
			ha.getAndAssert200(CUSTOMER_HOST_LOCAL_STATIC);
		}
	}

	@Test
	public void testFileUDProvider() throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			ha.disableHTTPAuthentication();
			ha.getAndAssert(401, CUSTOMER_HOST_LOCAL_FILE);

			ha.setupHTTPAuthentication("localhost", 3000, "membrane", "proxy");
			ha.getAndAssert200(CUSTOMER_HOST_LOCAL_FILE);
		}
	}
}