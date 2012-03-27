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

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.test.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.BufferLogger;

public class LoadBalancerSession3Test extends DistributionExtractingTestcase {
	
	/**
	 * The test as described in README.txt, but "wsimport" (previously called by ant)
	 * was removed and is run directly from this test before everything else. Thereby
	 * we can use a Maven dependency on wsimport and do not have to download it ourselves.
	 */
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-session-3");
		
		AssertUtils.replaceInFile(new File(base, "lb-session.proxies.xml"), "8080", "3023");
		AssertUtils.replaceInFile(new File(base, "src/com/predic8/chat/Client.java"), "8080", "3023");
		AssertUtils.replaceInFile(new File(base, "data/ChatService.wsdl"), "8080", "3023");
		
		Process2 sl = new Process2.Builder().in(base).script("router").waitForMembrane().start();
		try {

			File buildXML = new File(base, "build.xml");

			// remove <exec...</exec> from build.xml
			String s = Pattern.compile("<exec.*</exec>", Pattern.DOTALL).matcher(FileUtils.readFileToString(buildXML)).replaceAll("");
			FileUtils.writeStringToFile(buildXML, s);

			File classes = new File(base, "build" + File.separator + "classes");
			classes.mkdirs();
			File source = new File(base, "src");
			source.mkdirs();

			// run "wsimport" generating java sources
			Assert.assertTrue(new com.sun.tools.ws.wscompile.WsimportTool(System.out).run(new String[] {
					"-quiet",
					"-Xnocompile",
					new File(base, "data" + File.separator + "ChatService.wsdl").getAbsolutePath(),
					"-s",
					source.getAbsolutePath()
			}));
			
			// call "ant compile" now so that both antNodeX processes do call it at the same time
			BufferLogger loggerCompile = new BufferLogger();
			Process2 antCompile = new Process2.Builder().in(base).withWatcher(loggerCompile).executable("ant compile").start();
			try {
				int result = antCompile.waitFor(60000);
				if (result != 0)
					throw new AssertionError("'ant compile' returned non-zero " + result + ":\r\n" + loggerCompile.toString());
			} finally {
				antCompile.killScript();
			}
			

			BufferLogger loggerNode1 = new BufferLogger();
			BufferLogger loggerNode2 = new BufferLogger();
			Process2 antNode1 = new Process2.Builder().in(base).withWatcher(loggerNode1).executable("ant run-node1").start();
			try {
				Process2 antNode2 = new Process2.Builder().in(base).withWatcher(loggerNode2).executable("ant run-node2").start();
				try {
					
					LoadBalancerUtil.addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4000);
					LoadBalancerUtil.addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4001);
					
					Thread.sleep(1000); // wait for nodes to come up
					
					Process2 antClient = new Process2.Builder().in(base).executable("ant run-client -Dlogin=jim").start();
					try {
						antClient.waitFor(60000);
					} finally {
						antClient.killScript();
					}
					
				} finally {
					antNode2.killScript();
				}
			} finally {
				antNode1.killScript();
			}

			AssertUtils.assertContains("Hallo World", loggerNode1.toString());
			AssertUtils.assertContainsNot("Hallo World", loggerNode2.toString());
		} finally {
			sl.killScript();
		}

	}

}
