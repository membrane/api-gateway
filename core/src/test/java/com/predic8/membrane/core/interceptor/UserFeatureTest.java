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
package com.predic8.membrane.core.interceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;

public class UserFeatureTest {

	private static Router router;

	static List<String> labels, inverseLabels;

	@BeforeAll
	public static void setUp() throws Exception {
		router = Router.init("classpath:/userFeature/proxies.xml");
		MockInterceptor.clear();

		labels = new ArrayList<>(Arrays.asList(new String[]{"Mock1", "Mock3", "Mock4", "Mock7"}));
		inverseLabels = new ArrayList<>(Arrays.asList(new String[]{"Mock7", "Mock6", "Mock5", "Mock4", "Mock2", "Mock1"}));
	}

	@AfterAll
	public static void tearDown() throws Exception {
		router.shutdown();
	}

	private void callService(String s) throws HttpException, IOException {
		HttpClient httpClient = new HttpClient();
		httpClient.executeMethod(new GetMethod("http://localhost:3030/" + s + "/"));
		httpClient.getHttpConnectionManager().closeIdleConnections(0);
	}


	@Test
	public void testInvocation() throws Exception {
		callService("ok");
		MockInterceptor.assertContent(labels, inverseLabels, new ArrayList<>());
	}

	@Test
	public void testAbort() throws Exception {
		callService("abort");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inverseLabels);
	}

	@Test
	public void testFailInRequest() throws Exception {
		labels.add("Mock8");

		callService("failinrequest");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inverseLabels);
	}

	@Test
	public void testFailInResponse() throws Exception {
		labels.add("Mock9");

		callService("failinresponse");
		MockInterceptor.assertContent(labels, Arrays.asList("Mock9"), inverseLabels);
	}

	@Test
	public void testFailInAbort() throws Exception {
		labels.add("Mock10");
		inverseLabels.add(0, "Mock10");

		callService("failinabort");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inverseLabels);
	}


}
