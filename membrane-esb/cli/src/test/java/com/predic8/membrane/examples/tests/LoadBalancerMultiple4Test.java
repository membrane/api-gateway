package com.predic8.membrane.examples.tests;

import static com.predic8.membrane.examples.AssertUtils.getAndAssert;
import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.tests.LoadBalancerUtil.assertNodeStatus;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerMultiple4Test extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("loadbalancer-multiple-4")).script("router").waitForMembrane().start();
		try {
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));

			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:8081/service"));
			assertEquals(4, LoadBalancerUtil.getRespondingNode("http://localhost:8081/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:8081/service"));
			assertEquals(4, LoadBalancerUtil.getRespondingNode("http://localhost:8081/service"));

			String status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "UP");

			getAndAssert(204, "http://localhost:9010/clustermanager/down?balancer=balancer1&host=localhost&port=4001");
			Thread.sleep(1000);
			
			status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "DOWN");
			
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:8080/service"));
			
		} finally {
			sl.killScript();
		}
	}

	
	

}
