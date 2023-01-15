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

import static com.predic8.membrane.test.AssertUtils.disableHTTPAuthentication;
import static com.predic8.membrane.test.AssertUtils.getAndAssert;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.test.AssertUtils.setupHTTPAuthentication;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.util.Process2;

public class BasicAuthTest extends DistributionExtractingTestcase {
	public static final String CUSTOMER_HOST_LOCAL = "http://localhost:2000/";
	public static final String CUSTOMER_HOST_REMOTE = "http://www.thomas-bayer.com/";
	public static final String CUSTOMER_PATH = "samples/sqlrest/CUSTOMER/7/";

	@Override
	protected String getExampleDirName() {
		return "basic-auth";
	}

	@Test
	public void test() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			disableHTTPAuthentication();
			getAndAssert(401, CUSTOMER_HOST_LOCAL + CUSTOMER_PATH);

			setupHTTPAuthentication("localhost", 2000, "alice", "membrane");
			getAndAssert200(CUSTOMER_HOST_LOCAL + CUSTOMER_PATH);
		}
	}
}