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

package com.predic8.membrane.examples.tests.ssl;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;

import static com.predic8.membrane.test.AssertUtils.*;

public class SSLServerApiWithTlsTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "ssl/api-with-tls";
	}

	@Test
	public void test() throws Exception {
		replaceInFile2("proxies.xml", "443", "3023");

		try(Process2 ignored = startServiceProxyScript()) {
			trustAnyHTTPSServer(3023);
			assertContains("success", getAndAssert200("https://localhost:3023/axis2/services/BLZService?wsdl"));
		}
	}
}
