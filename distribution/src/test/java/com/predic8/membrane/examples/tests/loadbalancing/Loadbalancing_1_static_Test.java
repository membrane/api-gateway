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
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.tests.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.AssertUtils;

public class Loadbalancing_1_static_Test extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "loadbalancing/1-static";
	}

	@Test
	public void test() throws Exception {

		replaceInFile2("proxies.xml", "8080", "3023");

		try(Process2 ignored = startServiceProxyScript()) {

			assertEquals(1, getRespondingNode("http://localhost:4000/"));
			assertEquals(2, getRespondingNode("http://localhost:4001/"));
			assertEquals(3, getRespondingNode("http://localhost:4002/"));

			checkWhatNodesAreResponding(new int[]{1,2,3});
		}
	}
}
