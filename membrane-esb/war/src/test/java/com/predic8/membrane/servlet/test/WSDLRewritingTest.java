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

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WSDLRewritingTest {

	@Parameters
	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] { 
				{ 3021 }, // jetty port embedding membrane
				{ 3025 }, // membrane backend test port
				{ 3026 }, // membrane own proxy port
				});
	}

	private final int port;
	
	public WSDLRewritingTest(int port) {
		this.port = port;
	}
	
	@Test
	public void testWSDLRewritten() throws ClientProtocolException, IOException {
		assertContains("localhost:" + port, getAndAssert200(getBaseURL() + "ArticleService.wsdl"));
	}

	private String getBaseURL() {
		return "http://localhost:" + port + "/";
	}
	
}
