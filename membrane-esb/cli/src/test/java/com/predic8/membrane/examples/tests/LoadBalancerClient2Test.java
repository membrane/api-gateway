package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerClient2Test extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-client-2");
		Process2 sl = new Process2.Builder().in(base).script("router").waitForMembrane().start();
		try {
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:4000/"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:4001/"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:4002/"));
			
			addNodeViaScript(base, "localhost", 4000);
			
			Thread.sleep(1000);
			
			AssertUtils.assertContains("localhost:4000", 
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"));

			addNodeViaScript(base, "localhost", 4001);
			addNodeViaScript(base, "localhost", 4002);
			
			Thread.sleep(100);
			
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));

			removeNodeViaScript(base, "localhost", 4000);
			
			Thread.sleep(100);
			
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
		} finally {
			sl.killScript();
		}
		
		sl = new Process2.Builder().in(base).script("router-secured").waitForMembrane().start();
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
		Process2 lbclient = new Process2.Builder().in(base).
				executable("cmd /c lbclient.bat " + command + " " + nodeHost + " " + nodePort).
				start();
		Assert.assertEquals(expectedReturnCode, lbclient.waitFor(30000));
	}

}
