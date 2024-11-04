/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import com.predic8.membrane.core.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdjustContentLengthIntegrationTest {

	private static Router router;

	@BeforeAll
	public static void setUp() throws Exception {
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(createMonitorRule());
		router.getRuleManager().addProxyAndOpenPortIfNew(createEndpointRule());
		router.init();
	}

	private static ServiceProxy createMonitorRule() {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost","*", "*", 3000), "localhost", 4000);
		rule.getInterceptors().add(new StaticInterceptor() {{
			setTextTemplate("Ping Pong");
		}});
		return rule;
	}

	private static ServiceProxy createEndpointRule() {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost","*", "*", 4000), "localhost", 80);
		rule.getInterceptors().add(new StaticInterceptor() {{
			setTextTemplate("Pong");
		}});
		rule.getInterceptors().add(new ReturnInterceptor());
		return rule;
	}

	@Test
	public void testAdjustContentLength() throws Exception {
		GetMethod directRequest = getDirectRequest();
		new HttpClient().executeMethod(directRequest);

		GetMethod monitoredRequest = getMonitoredRequest();
		new HttpClient().executeMethod(monitoredRequest);

		assertTrue(directRequest.getResponseContentLength() == directRequest
				.getResponseBody().length);
		assertTrue(monitoredRequest.getResponseContentLength() == monitoredRequest
				.getResponseBody().length);

		assertTrue(directRequest.getResponseContentLength() != monitoredRequest
				.getResponseContentLength());

	}

	@AfterAll
	public static void tearDown() throws Exception {
		router.shutdown();
	}

	private GetMethod getDirectRequest() {
        return new GetMethod(
                "http://localhost:4000/");
	}

	private GetMethod getMonitoredRequest() {
        return new GetMethod(
                "http://localhost:3000/");
	}
}
