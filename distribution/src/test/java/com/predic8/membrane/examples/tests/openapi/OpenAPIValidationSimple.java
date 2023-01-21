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

package com.predic8.membrane.examples.tests.openapi;

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import static com.predic8.membrane.test.AssertUtils.*;

public class OpenAPIValidationSimple extends DistributionExtractingTestcase {

	final String createPersonValid = """
			{"name": "Johannes Gutenberg","age": 78}
			""";

	final String createPersonInvalid = """
			{"name": "Johannes Gutenberg","age": -10}
			""";

	final String validationResult = """
			{
			  "method" : "POST",
			  "uriTemplate" : "/persons",
			  "path" : "/persons",
			  "validationErrors" : {
				"REQUEST/BODY#/age" : [ {
				  "message" : "-10 is smaller than the minimum of 0",
				  "complexType" : "Person",
				  "schemaType" : "integer"
				} ]
			  }
			}
""";

	@Override
	protected String getExampleDirName() {
		return "openapi/validation-simple";
	}

	@Test
	public void test() throws Exception {

		try(Process2 ignored = startServiceProxyScript()) {
			assertContains("success", postAndAssert(201, URL_2000 + "/persons", CONTENT_TYPE_APP_JSON_HEADER, createPersonValid));
			JSONAssert.assertEquals(validationResult, postAndAssert(400, URL_2000 + "/persons", CONTENT_TYPE_APP_JSON_HEADER, createPersonInvalid), true);
		}
	}
}
