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

import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.tests.LoadBalancerUtil.assertNodeStatus;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.test.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerClient2Test extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-client-2");
		
		AssertUtils.replaceInFile(new File(base, "proxies.xml"), "8080", "3023");
		AssertUtils.replaceInFile(new File(base, "lb-client-secured.proxies.xml"), "8080", "3023");
		
		Process2 sl = new Process2.Builder().in(base).script("service-proxy").waitForMembrane().start();
		try {
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:4000/"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:4001/"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:4002/"));
			
			addNodeViaScript(base, "localhost", 4000);
			
			Thread.sleep(1000);
			
			assertNodeStatus(
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"),
					"localhost", 4000, "UP");

			addNodeViaScript(base, "localhost", 4001);
			addNodeViaScript(base, "localhost", 4002);
			
			Thread.sleep(100);
			
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));

			removeNodeViaScript(base, "localhost", 4000);
			
			Thread.sleep(100);
			
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
		} finally {
			sl.killScript();
		}
		
		sl = new Process2.Builder().in(base).script("service-proxy-secured").waitForMembrane().start();
		try {
			controlNodeViaScript(1, base, "up", "localhost", 4000); // 1 indicates failure
			
			File propFile = new File(base, "client.properties");
			writeStringToFile(propFile, readFileToString(propFile).replace("#", ""));
			
			Thread.sleep(1000);
			
			addNodeViaScript(base, "localhost", 4000);
			
			Thread.sleep(1000);
			
			AssertUtils.setupHTTPAuthentication("localhost", 9000, "admin", "admin");
			AssertUtils.assertContains("localhost:4000", 
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"));

		} finally {
			sl.killScript();
		}

	}


	private void addNodeViaScript(File base, String nodeHost, int nodePort) throws IOException, InterruptedException {
		controlNodeViaScript(base, "up", nodeHost, nodePort);
	}
	
	private void removeNodeViaScript(File base, String nodeHost, int nodePort) throws IOException, InterruptedException {
		controlNodeViaScript(base, "down", nodeHost, nodePort);
	}
	
	private void controlNodeViaScript(File base, String command, String nodeHost, int nodePort) throws IOException, InterruptedException {
		controlNodeViaScript(0, base, command, nodeHost, nodePort);
	}
	
	private void controlNodeViaScript(int expectedReturnCode, File base, String command, String nodeHost, int nodePort) throws IOException, InterruptedException {
		String line;
		if (Process2.isWindows())
			line = "cmd /c lbclient.bat " + command + " " + nodeHost + " " + nodePort;
		else
			line = "bash lbclient.sh " + command + " " + nodeHost + " " + nodePort;
		
		Process2 lbclient = new Process2.Builder().in(base).executable(line).start();
		try {
			Assert.assertEquals(expectedReturnCode, lbclient.waitFor(30000));
		} finally {
			lbclient.killScript();
		}
	}

}
