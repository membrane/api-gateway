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

package com.predic8.membrane.servlet.test;

import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.test.StringAssertions.assertContains;

public class WSDLRewritingTest {

	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] {
				{ 3021 }, // jetty port embedding membrane
				{ 3025 }, // membrane backend test port
				{ 3026 }, // membrane own proxy port
		});
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testWSDLRewritten(int port) throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			assertContains("localhost:" + port, ha.getAndAssert200(getBaseURL(port) + "ArticleService.wsdl"));
		}
	}

	private String getBaseURL(int port) {
		return "http://localhost:" + port + "/";
	}

}
