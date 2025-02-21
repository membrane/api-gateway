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

import com.predic8.membrane.core.util.OSUtil;
import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static com.predic8.membrane.examples.tests.loadbalancing.LoadBalancerUtil.addLBNodeViaHTML;
import static com.predic8.membrane.test.StringAssertions.assertContains;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;
import static java.lang.Thread.sleep;

public class Loadbalancing4XmlSessionExampleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/4-xml-session";
	}

	/**
	 * The test as described in README.txt, but "wsimport" (previously called by ant)
	 * was removed and is run directly from this test before everything else. Thereby
	 * we can use a Maven dependency on wsimport and do not have to download it ourselves.
	 */
	@Test
	public void test() throws Exception {
		replaceInFile2("proxies.xml", "8080", "3023");
		replaceInFile2("src/main/java/com/predic8/chat/Client.java", "8080", "3023");
		replaceInFile2("data/ChatService.wsdl", "8080", "3023");

		try(Process2 ignored = startServiceProxyScript()) {
			// call "mvn package" now so that both antNodeX processes do call it at the same time
			BufferLogger loggerCompile = new BufferLogger();
			try(Process2  mvnPackage = new Process2.Builder().in(baseDir).withWatcher(loggerCompile).executable(isWindows() ? "cmd /c mvn package" : "mvn package").start()) {
				int result = mvnPackage.waitForExit(40000);
				if (result != 0)
					throw new AssertionError("'mvn package' returned non-zero " + result + ":\r\n" + loggerCompile);
			}

			BufferLogger loggerNode1 = new BufferLogger();
			BufferLogger loggerNode2 = new BufferLogger();
			try(Process2 ignored1 = new Process2.Builder().in(baseDir).withWatcher(loggerNode1).executable(isWindows() ? "cmd /c mvn exec:java@node1" : "mvn exec:java@node1").start()) {
				try(Process2 ignored2 = new Process2.Builder().in(baseDir).withWatcher(loggerNode2).executable(isWindows() ? "cmd /c mvn exec:java@node2" :"mvn exec:java@node2").start()) {

					addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4000);
					addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4001);

					sleep(200); // wait for nodes to come up

					try(Process2 mvnExec = new Process2.Builder().in(baseDir).executable(isWindows() ? "cmd /c mvn exec:java@client" : "mvn exec:java@client").start()) {
						mvnExec.waitForExit(30000);
					}
				}
			}

			assertContains("Hallo World", loggerNode1.toString());
			assertContainsNot("Hallo World", loggerNode2.toString());
		}
	}
}
