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

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.*;

import static io.restassured.RestAssured.*;
import static java.util.Arrays.*;

class UserFeatureTest {

	public static final String MOCK_1 = "mock1";
	public static final String MOCK_3 = "mock3";
	public static final String MOCK_4 = "mock4";
	public static final String MOCK_7 = "mock7";
	public static final String MOCK_5 = "mock5";
	public static final String MOCK_6 = "mock6";
	public static final String MOCK_2 = "mock2";
	private static Router router;

	static List<String> labels, inverseLabels, inversAbortLabels;

	@BeforeAll
	static void setUp() {
		router = Router.init("classpath:/userFeature/proxies.xml");
		MockInterceptor.clear();
	}

	@BeforeEach
	void beforeEach() {
		MockInterceptor.clear();
		labels = new ArrayList<>(asList(MOCK_1, MOCK_3, MOCK_4, MOCK_7));
		inverseLabels = new ArrayList<>(asList(MOCK_7, MOCK_6, MOCK_5, MOCK_4, MOCK_2, MOCK_1));
		inversAbortLabels = new ArrayList<>(asList(MOCK_7, MOCK_4, MOCK_1));
	}

	@AfterAll
	static void tearDown() throws Exception {
		router.shutdown();
	}

	private void callService(String s) {
		given()
			.get("http://localhost:3030/%s/".formatted(s))
			.then().log();
	}

	@Test
	void testInvocation() throws Exception {
		callService("ok");
		MockInterceptor.assertContent(labels, inverseLabels, new ArrayList<>());
	}

	@Test
	void testAbort() throws Exception {
		callService("abort");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inversAbortLabels);
	}

	@Test
	void testFailInRequest() throws Exception {
		labels.add("mock8");

		callService("failinrequest");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inversAbortLabels);
	}

	@Test
	void testFailInResponse() throws Exception {
		labels.add("mock9");

		callService("failinresponse");
		MockInterceptor.assertContent(labels, List.of("mock9"), inversAbortLabels);
	}

	@Test
	void testFailInAbort() throws Exception {
		labels.add("mock10");
		inversAbortLabels.add(0, "mock10");

		callService("failinabort");
		MockInterceptor.assertContent(labels, new ArrayList<>(), inversAbortLabels);
	}
}