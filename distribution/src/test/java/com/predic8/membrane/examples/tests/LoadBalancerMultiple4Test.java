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

import static com.predic8.membrane.test.AssertUtils.getAndAssert;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static com.predic8.membrane.examples.tests.LoadBalancerUtil.assertNodeStatus;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.test.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class LoadBalancerMultiple4Test extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {

		File base = getExampleDir("loadbalancer-multiple-4");

		AssertUtils.replaceInFile(new File(base, "proxies.xml"), "8080", "3023");
		AssertUtils.replaceInFile(new File(base, "proxies.xml"), "8081", "3024");

		Process2 sl = new Process2.Builder().in(base).script("service-proxy").waitForMembrane().start();
		try {
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(2, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));

			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3024/service"));
			assertEquals(4, LoadBalancerUtil.getRespondingNode("http://localhost:3024/service"));
			assertEquals(3, LoadBalancerUtil.getRespondingNode("http://localhost:3024/service"));
			assertEquals(4, LoadBalancerUtil.getRespondingNode("http://localhost:3024/service"));

			String status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "UP");

			getAndAssert(204, "http://localhost:9010/clustermanager/down?balancer=balancer1&host=localhost&port=4001");
			Thread.sleep(1000);

			status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "DOWN");

			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));
			assertEquals(1, LoadBalancerUtil.getRespondingNode("http://localhost:3023/service"));

		} finally {
			sl.killScript();
		}
	}




}
