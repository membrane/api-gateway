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

package com.predic8.membrane.examples.tutorials.rest;

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.AssertUtils.*;

/**
 * See: https://membrane-api.io/tutorials/rest/
 *
 * Needs an Internet connection to work!
 */
public class TutorialRestInitialTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "../tutorials/rest";
	}

	@Test
	public void testStart() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("Shop API", getAndAssert200(URL_2000));
			assertContains("Membrane Service Proxy Administration", getAndAssert200("http://localhost:9000/admin/"));
		}
	}
}
