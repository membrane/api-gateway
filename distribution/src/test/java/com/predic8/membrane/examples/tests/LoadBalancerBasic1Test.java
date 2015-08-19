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

import static com.predic8.membrane.examples.tests.LoadBalancerUtil.addLBNodeViaHTML;
import static com.predic8.membrane.test.AssertUtils.getAndAssert;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.test.AssertUtils;

public class LoadBalancerBasic1Test extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		File base = getExampleDir("loadbalancer-basic-1");

		AssertUtils.replaceInFile(new File(base, "proxies.xml"), "8080", "3023");

		Process2 sl = new Process2.Builder().in(base).script("service-proxy").waitForMembrane().start();
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
