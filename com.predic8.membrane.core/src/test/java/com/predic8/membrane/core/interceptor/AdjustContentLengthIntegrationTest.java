/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.*;

import com.predic8.membrane.core.Router;

public class AdjustContentLengthIntegrationTest extends TestCase {

	private Router router;

	@Before
	public void setUp() throws Exception {
		router = Router
				.init("classpath:/adjustContentLength/monitor-beans.xml");
		router.getConfigurationManager().loadConfiguration(
				"classpath:/adjustContentLength/xslt.proxies.xml");
	}

	@Test
	public void testAdjustContentLength() throws Exception {
		GetMethod direktRequest = getDirektRequest();
		new HttpClient().executeMethod(direktRequest);

		GetMethod monitoredRequest = getMonitoredRequest();
		new HttpClient().executeMethod(monitoredRequest);

		assertTrue(direktRequest.getResponseContentLength() == direktRequest
				.getResponseBody().length);
		assertTrue(monitoredRequest.getResponseContentLength() == monitoredRequest
				.getResponseBody().length);

		assertTrue(direktRequest.getResponseContentLength() != monitoredRequest
				.getResponseContentLength());

	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	private GetMethod getDirektRequest() {
		GetMethod get = new GetMethod(
				"http://thomas-bayer.com/sqlrest/CUSTOMER/2/");
		return get;
	}

	private GetMethod getMonitoredRequest() {
		GetMethod get = new GetMethod(
				"http://localhost:5000/sqlrest/CUSTOMER/2/");
		return get;
	}
}
