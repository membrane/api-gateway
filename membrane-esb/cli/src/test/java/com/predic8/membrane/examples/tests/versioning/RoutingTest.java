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
package com.predic8.membrane.examples.tests.versioning;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.postAndAssert;
import static com.predic8.membrane.test.AssertUtils.replaceInFile;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.ConsoleLogger;

public class RoutingTest extends DistributionExtractingTestcase {
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("versioning/routing");
		
		String header[] = new String[] { "Content-Type", "text/xml" };
		String request_v11 = FileUtils.readFileToString(new File(base, "request_v11.xml"));
		String request_v20 = FileUtils.readFileToString(new File(base, "request_v20.xml"));
		
		replaceInFile(new File(base, "proxies.xml"), "8080", "3024");
		replaceInFile(new File(base, "proxies.xml"), "2000", "3025");
		replaceInFile(new File(base, "src/com/predic8/contactservice/Launcher.java"), "8080", "3024");
		
		Process2 sl = new Process2.Builder().in(base).script("router").withWatcher(new ConsoleLogger()).waitForMembrane().start();
		try {
			Process2 antNode1 = new Process2.Builder().in(base).waitAfterStartFor("run:").executable("ant run").withWatcher(new ConsoleLogger()).start();
			try {
				Thread.sleep(2000); // wait for Endpoints to start
				
				// directly talk to versioned endpoints
				assertContains("1.1", postAndAssert(200, "http://localhost:3024/ContactService/v11", header, request_v11));
				assertContains("2.0", postAndAssert(200, "http://localhost:3024/ContactService/v20", header, request_v20));
				
				// talk to wrong endpoint
				postAndAssert(500, "http://localhost:3024/ContactService/v20", header, request_v11);
				
				// talk to proxy
				assertContains("1.1", postAndAssert(200, "http://localhost:3025/ContactService", header, request_v11));
				assertContains("2.0", postAndAssert(200, "http://localhost:3025/ContactService", header, request_v20));

			} finally {
				antNode1.killScript();
			}
			
		} finally {
			sl.killScript();
		}

	}
}
