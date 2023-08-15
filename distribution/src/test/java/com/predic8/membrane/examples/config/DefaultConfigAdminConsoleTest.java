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

package com.predic8.membrane.examples.config;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.test.AssertUtils.replaceInFile;
import static com.predic8.membrane.test.AssertUtils.setupHTTPAuthentication;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.readFileToString;

import java.io.File;

import org.junit.jupiter.api.*;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.examples.util.ProxiesXmlUtil;

public class DefaultConfigAdminConsoleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "..";
	}

	@Test
	public void test() throws Exception {

		File proxies = new File(baseDir, "conf/proxies.xml");
		File proxiesFull = new File(baseDir, "conf/proxies-full-sample.xml");

		replaceInFile(proxies, "9000", "2003");
		replaceInFile(proxiesFull, "9000", "2003");

		try(Process2 sl = startServiceProxyScript()) {
			setupHTTPAuthentication("localhost", 2003, "admin", "membrane");
			assertContains("Membrane API Gateway Administration", getAndAssert200("http://localhost:2003/admin/"));

			new ProxiesXmlUtil(proxies).updateWith(readFileToString(proxiesFull, UTF_8), sl);
			readFileFromBaseDir("conf/proxies-full-sample.xml");

			setupHTTPAuthentication("localhost", 2001, "admin", "membrane");
			assertContains("Routing Configuration", getAndAssert200("http://localhost:2001/static/proxies.xml"));
		}
	}
}
