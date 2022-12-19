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

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.test.AssertUtils;

public class REST2SOAPJSONTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("rest2soap-json");
		AssertUtils.replaceInFile(new File(baseDir, "proxies.xml"), "2000", "2043");
		Process2 sl = new Process2.Builder().in(baseDir).script("service-proxy").waitForMembrane().start();
		try {
			assertContains("\"bic\":\"COLSDE33XXX\"",
					getAndAssert200("http://localhost:2043/bank/37050198",
							new String[] { "Accept", "application/json" } ));
			assertContains("\"bic\":\"GENODE61KIR\"",
					getAndAssert200("http://localhost:2043/bank/66762332",
							new String[] { "Accept", "application/json" } ));
		} finally {
			sl.killScript();
		}
	}


}
