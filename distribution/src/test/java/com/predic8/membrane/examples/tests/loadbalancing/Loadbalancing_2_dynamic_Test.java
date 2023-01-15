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

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import com.predic8.membrane.test.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.examples.tests.loadbalancing.LoadBalancerUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class Loadbalancing_2_dynamic_Test extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/2-dynamic";
	}

	@Test
	public void addingNodesDynamicallyUsingTheAdminConsole() throws Exception {
		replaceInFile2("proxies.xml", "8080", "3023");

		try(Process2 sl = startServiceProxyScript()) {

			// Make sure backends are running
			assertEquals(1, getRespondingNode("http://localhost:4000/"));
			assertEquals(2, getRespondingNode("http://localhost:4001/"));
			assertEquals(3, getRespondingNode("http://localhost:4002/"));

			// Add dynamically a node on port 4000
			addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4000);

			assertNodeStatus(
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"),
					"localhost", 4000, "UP");

			// Because just one node is running we should get always the same node
			checkWhatNodesAreResponding(new int[]{1});

			// Add dynamically a node on port 4001
			addLBNodeViaHTML("http://localhost:9000/admin/", "localhost", 4001);

			assertNodeStatus(
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"),
					"localhost", 4001, "UP");

			checkWhatNodesAreResponding(new int[]{1,2});
		}
	}

	@Test
	public void addingNodesDynamicallyUsingTheCluserAPI() throws Exception {
		replaceInFile2("proxies.xml", "8080", "3023");

		try(Process2 ignored = startServiceProxyScript()) {

			// Make sure backends are running
			assertEquals(1, getRespondingNode("http://localhost:4000/"));
			assertEquals(2, getRespondingNode("http://localhost:4001/"));
			assertEquals(3, getRespondingNode("http://localhost:4002/"));

			// Start Node 1
			getAndAssert(204, "http://localhost:9010/clustermanager/up?host=localhost&port=4000");

			assertNodeStatus(
					getAndAssert200("http://localhost:9000/admin/clusters/show?cluster=Default"),
					"localhost", 4000, "UP");

			// Because just one node is running we should get always the same node
			assertEquals(1, getRespondingNode("http://localhost:3023/service"));

			// Start Node 2
			getAndAssert(204, "http://localhost:9010/clustermanager/up?host=localhost&port=4001");
			checkWhatNodesAreResponding(new int[]{1,2});

			// Start Node 3
			getAndAssert(204, "http://localhost:9010/clustermanager/up?host=localhost&port=4002");
			checkWhatNodesAreResponding(new int[]{1,2,3});

			// Stop Node 3
			getAndAssert(204, "http://localhost:9010/clustermanager/down?host=localhost&port=4002");
			checkWhatNodesAreResponding(new int[]{1,2});

			// Stop Node 2
			getAndAssert(204, "http://localhost:9010/clustermanager/down?host=localhost&port=4001");
			checkWhatNodesAreResponding(new int[]{1});

			// Stop Node 1
			getAndAssert(204, "http://localhost:9010/clustermanager/down?host=localhost&port=4000");
			getAndAssert(500,"http://localhost:3023/service");
		}
	}
}
