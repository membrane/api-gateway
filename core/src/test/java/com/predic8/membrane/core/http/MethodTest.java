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
package com.predic8.membrane.core.http;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class MethodTest {

	private static HttpRouter router;

	@BeforeAll
	public static void setUp() throws Exception {
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 4000), "predic8.de", 80);
		proxy.getInterceptors().add(new AbstractInterceptor() {
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				if (exc.getRequest().getMethod().equals("DELETE")) {
					exc.setResponse(Response.ok().build());
					return Outcome.RETURN;
				}
				return super.handleRequest(exc);
			}
		});
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy);
		router.init();
	}

	@Test
	public void testDELETE() throws Exception {
		HttpClient client = new HttpClient();

		DeleteMethod delete = new DeleteMethod("http://localhost:4000/method-test/");

		int status = client.executeMethod(delete);
		assertTrue(status < 400);
	}

	@AfterAll
	public static void tearDown() {
		router.shutdown();
	}

}
