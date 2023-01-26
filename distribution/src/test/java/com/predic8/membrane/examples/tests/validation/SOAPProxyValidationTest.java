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

package com.predic8.membrane.examples.tests.validation;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static java.io.File.*;

public class SOAPProxyValidationTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "validation" + separator + "soap-Proxy";
	}

	@Test
	public void test() throws Exception {
		try(Process2 ignored = startServiceProxyScript()) {
			String url = "http://localhost:2000/axis2/services/BLZService/getBankResponse";
			postAndAssert(200, url, CONTENT_TYPE_SOAP_HEADER, readFile("blz-soap.xml"));
			postAndAssert(400, url, CONTENT_TYPE_SOAP_HEADER, readFile("invalid-blz-soap.xml"));
		}
	}
}
