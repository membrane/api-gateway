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

import static com.predic8.membrane.core.AssertUtils.assertContains;
import static com.predic8.membrane.core.AssertUtils.getAndAssert200;
import static com.predic8.membrane.core.AssertUtils.setupHTTPAuthentication;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.junit.Test;

public class Basic {

	// integration test settings (corresponding to those in proxies.xml)
	private static final String MEMBRANE_ADMIN_HOST = "localhost";
	private static final int MEMBRANE_ADMIN_PORT = 3027;
	private static final String BASIC_AUTH_USER = "admin";
	private static final String BASIC_AUTH_PASSWORD = "membrane";
	
	@Test
	public void testAdminConsoleReachable() throws ClientProtocolException, IOException {
		setupHTTPAuthentication(MEMBRANE_ADMIN_HOST, MEMBRANE_ADMIN_PORT, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
		assertContains("ServiceProxies", getAndAssert200(getBaseURL() + "admin/"));
	}

	@Test
	public void testAdminConsoleJavascriptDownloadable() throws ClientProtocolException, IOException {
		setupHTTPAuthentication(MEMBRANE_ADMIN_HOST, MEMBRANE_ADMIN_PORT, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
		assertContains("jQuery", getAndAssert200(getBaseURL() + "jquery-ui/js/jquery-ui-1.8.13.custom.min.js"));
	}

	private String getBaseURL() {
		return "http://" + MEMBRANE_ADMIN_HOST + ":" + MEMBRANE_ADMIN_PORT + "/";
	}
	
}
