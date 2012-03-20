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

import static com.predic8.membrane.examples.AssertUtils.getAndAssert200;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.examples.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;

public class XSLTTest extends DistributionExtractingTestcase {
	
	@Test
	public void test() throws IOException, InterruptedException {
		File baseDir = getExampleDir("xslt");
		Process2 sl = new Process2.Builder().in(baseDir).script("router").waitForMembrane().start();
		try {
			String result = getAndAssert200(BasicAuthTest.CUSTOMER_HOST_REMOTE + BasicAuthTest.CUSTOMER_PATH);
			AssertUtils.assertContains("FIRSTNAME", result);
			
			result = getAndAssert200(BasicAuthTest.CUSTOMER_HOST_LOCAL + BasicAuthTest.CUSTOMER_PATH);
			AssertUtils.assertContains("first", result);
		} finally {
			sl.killScript();
		}
	}


}
