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

import static com.predic8.membrane.examples.tests.loadbalancing.LoadBalancerUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;

public class Loadbalancing5MultipleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/5-multiple";
	}

	@Test
	public void test() throws Exception {

		replaceInFile2("proxies.xml","8080", "3023");
		replaceInFile2("proxies.xml","8081", "3024");

		try(Process2 ignored = startServiceProxyScript()) {
			checkWhatNodesAreResponding(new int[]{1,2});

			assertEquals(3, getRespondingNode("http://localhost:3024/service"));
			assertEquals(4, getRespondingNode("http://localhost:3024/service"));
			assertEquals(3, getRespondingNode("http://localhost:3024/service"));
			assertEquals(4, getRespondingNode("http://localhost:3024/service"));

			String status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "UP");

			getAndAssert(204, "http://localhost:9010/clustermanager/down?balancer=balancer1&host=localhost&port=4001");
			Thread.sleep(1000);

			status = getAndAssert200("http://localhost:9000/admin/clusters/show?balancer=balancer1&cluster=Default");
			assertNodeStatus(status, "localhost", 4000, "UP");
			assertNodeStatus(status, "localhost", 4001, "DOWN");

			assertEquals(1, getRespondingNode("http://localhost:3023/service"));
			assertEquals(1, getRespondingNode("http://localhost:3023/service"));
		}
	}

}
