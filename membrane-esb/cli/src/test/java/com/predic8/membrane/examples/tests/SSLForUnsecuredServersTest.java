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

import static com.predic8.membrane.core.AssertUtils.assertContains;
import static com.predic8.membrane.core.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import com.predic8.membrane.core.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class SSLForUnsecuredServersTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
		File baseDir = getExampleDir("ssl-for-unsecured-servers");
		
		AssertUtils.replaceInFile(new File(baseDir, "unsecured-server.proxies.xml"), "443", "3023");
		
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			AssertUtils.trustAnyHTTPSServer(3023);
			
			assertContains("wsdl:documentation", getAndAssert200("https://localhost:3023/axis2/services/BLZService?wsdl"));
		} finally {
			sl.killScript();
		}
	}


}
