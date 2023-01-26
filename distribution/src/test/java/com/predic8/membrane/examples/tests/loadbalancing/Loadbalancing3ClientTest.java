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

package com.predic8.membrane.examples.tests.loadbalancing;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.examples.tests.loadbalancing.BalancerClientScriptUtil.*;
import static com.predic8.membrane.examples.tests.loadbalancing.LoadBalancerUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class Loadbalancing3ClientTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/3-client";
	}

	@Test
	public void test() throws Exception {

		replaceInFile2("proxies.xml", "8080", "3023");
		replaceInFile2("lb-client-secured.proxies.xml", "8080", "3023");

		try(Process2 ignored = startServiceProxyScript()) {
			assertEquals(1, getRespondingNode("http://localhost:4000/"));
			assertEquals(2, getRespondingNode("http://localhost:4001/"));
			assertEquals(3, getRespondingNode("http://localhost:4002/"));

			addNodeViaScript(baseDir, "localhost", 4000);

			sleep(1000);

			assertNodeStatus(
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"),
					"localhost", 4000, "UP");

			addNodeViaScript(baseDir, "localhost", 4001);
			addNodeViaScript(baseDir, "localhost", 4002);

			sleep(100);

			assertEquals(1, getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, getRespondingNode("http://localhost:3023/service"));
			assertEquals(3, getRespondingNode("http://localhost:3023/service"));

			removeNodeViaScript(baseDir, "localhost", 4000);

			sleep(100);

			assertEquals(2, getRespondingNode("http://localhost:3023/service"));
			assertEquals(3, getRespondingNode("http://localhost:3023/service"));
		}

		try(Process2 ignored = startServiceProxyScript(null,"service-proxy-secured")) {
			controlNodeViaScript(1, baseDir, "up", "localhost", 4000); // 1 indicates failure

			File propFile = new File(baseDir, "client.properties");
			writeStringToFile(propFile, readFileToString(propFile, UTF_8).replace("#", ""), UTF_8);

			sleep(100);

			addNodeViaScript(baseDir, "localhost", 4000);

			sleep(100);

			setupHTTPAuthentication("localhost", 9000, "admin", "admin");
			assertContains("localhost:4000", getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"));
		}
	}
}
