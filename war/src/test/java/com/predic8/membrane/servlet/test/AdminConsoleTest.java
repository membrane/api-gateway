/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.servlet.test;

import com.predic8.membrane.test.HttpAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;

import static com.predic8.membrane.test.StringAssertions.assertContains;

public class AdminConsoleTest {

	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] {
				{ 3021 }, // jetty port embedding membrane
				{ 3027 }, // membrane admin console port
		});
	}

	private static final String BASIC_AUTH_USER = "admin";
	private static final String BASIC_AUTH_PASSWORD = "membrane";

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testAdminConsoleReachable(int port) throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			ha.setupHTTPAuthentication("localhost", port, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
			assertContains("ServiceProxies", ha.getAndAssert200(getBaseURL(port) + "admin/"));
		}
	}

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testAdminConsoleJavascriptDownloadable(int port) throws Exception {
		try (HttpAssertions ha = new HttpAssertions()) {
			ha.setupHTTPAuthentication("localhost", port, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
			assertContains("jQuery", ha.getAndAssert200(getBaseURL(port) + "admin/jquery-ui/js/jquery-ui-1.8.13.custom.min.js"));
		}
	}

	private String getBaseURL(int port) {
		return "http://localhost:" + port + "/";
	}
}
