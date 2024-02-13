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

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.Header.SOAP_ACTION;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.MalformedURLException;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import org.junit.jupiter.api.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;

public class SOAPProxyIntegrationTest {

	private static Router router;

	@BeforeAll
	public static void setup() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey(3000), null, 0);
		rule.getInterceptors().add(new SampleSoapServiceInterceptor());
		Router targetRouter = new HttpRouter();
		targetRouter.getRuleManager().addProxyAndOpenPortIfNew(rule);
		targetRouter.init();
	}

	@BeforeEach
	public void reset() throws Exception {
		router = Router.init("classpath:/soap-proxy.xml");
	}

	@AfterAll
	public static void shutdown() throws IOException {
		router.shutdown();
	}

	@Order(0)
	@Test
	public void targetProxyTest() throws IOException {
		getAndAssert200("http://localhost:3000?wsdl",
				new String[] {
						CONTENT_TYPE, TEXT_XML_UTF8,
						SOAP_ACTION, ""
				});
	}

	@Order(1)
	@Test
	public void test() throws Exception {
		getAndAssert200("http://localhost:2000/foo?wsdl",
				new String[] {
				CONTENT_TYPE, TEXT_XML_UTF8,
				SOAP_ACTION, ""
		});
	}

	@Order(2)
	@Test
	public void test2() throws Exception {
		String wsdl = getAndAssert200("http://localhost:2001/baz?wsdl");
		assertContains("location=\"http://localhost:2001/foo\"", wsdl);
	}

	@Order(3)
	@Test
	public void test3() throws Exception {
		String wsdl = getAndAssert200("http://localhost:2002/baz?wsdl");
		assertContains("location=\"http://localhost:2001/foo\"", wsdl);
	}

}
