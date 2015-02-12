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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.predic8.membrane.test.AssertUtils;
import com.predic8.membrane.examples.DistributionExtractingTestcase;
import com.predic8.membrane.examples.Process2;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;

public class LoggingTest extends DistributionExtractingTestcase {

	@Test
	public void test() throws IOException, InterruptedException {
		Process2 sl = new Process2.Builder().in(getExampleDir("logging")).script("service-proxy").waitForMembrane().start();
		try {
			SubstringWaitableConsoleEvent logged = new SubstringWaitableConsoleEvent(sl, "HTTP/1.1");
			AssertUtils.getAndAssert200("http://localhost:2000/");
			assertEquals(true, logged.occurred());
		} finally {
			sl.killScript();
		}
	}

}
