package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.tests.LoadBalancerUtil.addLBNodeViaHTML;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerBasic1Test extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-basic-1");
		
		AssertUtils.replaceInFile(new File(base, "lb-basic.proxies.xml"), "8080", "3023");
		
		Process2 sl = new Process2.Builder().in(base).script("router").waitForMembrane().start();
		try {
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:4000/"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:4001/"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:4002/"));
			
			addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4000);
			addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4001);
			
			Thread.sleep(1000);
			
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			
			getAndAssert(204, "http://localhost:9010/clustermanager/up?host=localhost&port=4002");

			AssertUtils.assertContains("localhost:4002", 
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"));

			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			
			
		} finally {
			sl.killScript();
		}
	}
	
	

}
