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

package com.predic8.membrane.examples.withinternet.test;

import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.api.Test;

public class ACLExampleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "security/access-control-list";
	}

	@Test
	public void test() throws Exception {
		try(Process2 ignored = startServiceProxyScript(); HttpAssertions ha = new HttpAssertions()) {
			ha.getAndAssert200("http://localhost:2000/");

			// this request succeeds through membrane, but fails on the backend with 404
			ha.getAndAssert(404, "http://localhost:2000/contacts/");

			ha.getAndAssert(401, "http://localhost:2000/open-source/");
		}
	}
}