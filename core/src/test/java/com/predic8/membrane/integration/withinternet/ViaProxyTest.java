/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.integration.withinternet;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.client.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.*;
import static io.restassured.RestAssured.*;


public class ViaProxyTest {

	static HttpRouter proxyRouter;
	static HttpRouter router;

	@BeforeAll
	static void setUp() throws Exception {
		ProxyConfiguration proxy = new ProxyConfiguration();
		proxy.setHost("localhost");
		proxy.setPort(3128);

		proxyRouter = new HttpRouter(proxy);
		proxyRouter.getRuleManager().addProxy(new ProxyRule(new ProxyRuleKey(3128)), MANUAL);
		proxyRouter.init();

		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost", "GET", ".*", 4000), "api.predic8.de", 443);
		rule.getTarget().setSslParser(new SSLParser());
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		router.init();
	}

	@Test
	void testPost() {
		when()
			.get("http://localhost:4000/shop/v2/products")
		.then()
			.statusCode(200);
	}

	@AfterAll
	static void tearDown() {
		router.shutdown();
		proxyRouter.shutdown();
	}
}
