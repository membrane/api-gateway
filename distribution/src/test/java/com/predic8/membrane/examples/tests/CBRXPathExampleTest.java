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

import com.predic8.membrane.examples.util.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;

public class CBRXPathExampleTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "cbr";
	}

	@Test
	public void test() throws Exception {

		try(Process2 ignored = startServiceProxyScript()) {
			// @formatter:off
			given()
				.body(readFile("order.xml"))
				.post(LOCALHOST_2000)
			.then()
				.statusCode(200)
				.body(Matchers.containsString("Normal"));

			given()
				.contentType(APPLICATION_XML)
				.body(readFile("express.xml"))
				.post(LOCALHOST_2000)
			.then()
				.statusCode(200)
				.body(Matchers.containsString("Express"));

			given()
				.body(readFile("import.xml"))
				.post(LOCALHOST_2000)
				.then()
				.statusCode(200)
				.body(Matchers.containsString("import"));
			// @formatter:on
		}
	}
}
