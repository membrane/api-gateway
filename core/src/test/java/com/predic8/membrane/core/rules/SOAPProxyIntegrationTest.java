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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML_UTF8;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;

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
	public void startRouter() throws Exception {
		router = Router.init("classpath:/soap-proxy.xml");
	}

	@AfterEach
	public void shutdownRouter() throws IOException {
		router.shutdown();
	}

	@Test
	public void targetProxyTest() {
		when()
			.get("http://localhost:3000/foo?wsdl")
		.then()
			.contentType(TEXT_XML_UTF8);
	}

	@Test
	public void rewriteSimpleTest() throws Exception {
		when()
			.get("http://localhost:2000/foo?wsdl")
		.then()
			.contentType(TEXT_XML);
	}

	@Test
	public void rewriteLocationTest() {
		when()
			.get("http://localhost:2001/foo?wsdl")
		.then()
			.body(containsString("location=\"http://localhost:2001/foo\""));
	}

	@Test
	public void rewriteHostInLocationTest() {
		when()
			.get("http://localhost:2002/baz?wsdl")
		.then()
			.body(containsString("location=\"http://localhost:2001/baz\""));
	}
}
