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

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.Test;

public class REST2SOAPJSONTest extends DistributionExtractingTestcase {

	static final String[] ACCEPT_HEADER = new String[] { "Accept", APPLICATION_JSON};

	@Override
	protected String getExampleDirName() {
		return "rest2soap-json";
	}

	@Test
	public void test() throws Exception {
		replaceInFile2("proxies.xml", "2000", "2043");
		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("\"bic\":\"COLSDE33XXX\"",
					getAndAssert200("http://localhost:2043/bank/37050198",ACCEPT_HEADER));
			assertContains("\"bic\":\"GENODE61KIR\"",
					getAndAssert200("http://localhost:2043/bank/66762332", ACCEPT_HEADER ));
		}
	}
}