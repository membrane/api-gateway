/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.rules;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;

import java.io.IOException;
import java.net.MalformedURLException;

import com.predic8.membrane.core.HttpRouter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;

public class SOAPProxyIntegrationTest {

	private static Router router;

	@BeforeAll
	public static void init() throws MalformedURLException {
		router = Router.init("classpath:/soap-proxy.xml");
	}

	@AfterAll
	public static void uninit() throws IOException {
		router.shutdown();
	}

	@Test
	public void test() throws Exception {
		getAndAssert200("http://localhost:2000/foo?wsdl",
				new String[] {
				Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8,
				Header.SOAP_ACTION, ""
		});
	}

	@Test
	public void test2() throws Exception {
		String wsdl = getAndAssert200("http://localhost:2001/baz?wsdl");
		assertContains("location=\"http://localhost:2001/foo\"", wsdl);
	}

	@Test
	public void test3() throws Exception {
		String wsdl = getAndAssert200("http://localhost:2002/myBLZService?wsdl");
		assertContains("location=\"http://localhost:2001/myBLZService\"", wsdl);
	}



}
