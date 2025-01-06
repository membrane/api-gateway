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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.soap.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;

public class SOAPProxyIntegrationTest {

	private static Router router;
	private static Router targetRouter;

	@BeforeAll
	public static void setup() throws Exception {
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey(3000), null, 0);
		proxy.getInterceptors().add(new SampleSoapServiceInterceptor());

		targetRouter = new HttpRouter();
		targetRouter.getRuleManager().addProxyAndOpenPortIfNew(proxy);
		targetRouter.init();
	}

	@AfterAll
	public static void teardown() {
		targetRouter.shutdown();
	}

	@BeforeEach
	public void startRouter() {
		router = Router.init("classpath:/soap-proxy.xml");
	}

	@AfterEach
	public void shutdownRouter() {
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
	public void rewriteSimpleTest() {
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
