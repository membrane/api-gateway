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

package com.predic8.membrane.examples.env;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.test.AssertUtils.replaceInFile;
import static com.predic8.membrane.test.AssertUtils.setupHTTPAuthentication;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.ProxiesXmlUtil;

public class DefaultConfigTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getMembraneHome();

		File proxies = new File(baseDir, "conf/proxies.xml");
		File proxiesFull = new File(baseDir, "conf/proxies-full-sample.xml");

		replaceInFile(proxies, "9000", "2003");
		replaceInFile(proxiesFull, "9000", "2003");

		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			setupHTTPAuthentication("localhost", 2003, "admin", "membrane");
			assertContains("Membrane Service Proxy Administration", getAndAssert200("http://localhost:2003/admin/"));

			ProxiesXmlUtil pxu = new ProxiesXmlUtil(proxies);
			pxu.updateWith(FileUtils.readFileToString(proxiesFull), sl);

			setupHTTPAuthentication("localhost", 2001, "admin", "membrane");
			assertContains("Routing Configuration", getAndAssert200("http://localhost:2001/static/proxies.xml"));
		} finally {
			sl.killScript();
		}
	}

}
