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

package com.predic8.membrane.examples.tests;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.interceptor.authentication.session.totp.OtpProvider;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.test.AssertUtils.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class LoginTest extends DistributionExtractingTestcase {

	@Override
	protected String getExampleDirName() {
		return "login";
	}

	@Test
	public void test() throws Exception {
		try (Process2 ignored = startServiceProxyScript()) {
			// @formatter:off
			// Check login form
			given().when()
				.get("http://localhost:2000/")
			.then()
				.statusCode(200)
				.body(containsString("Username:"))
				.body(containsString("Password:"));

			// Submit login credentials
			given()
				.contentType(APPLICATION_X_WWW_FORM_URLENCODED)
				.formParam("username", "john")
				.formParam("password", "password")
			.when()
				.post("http://localhost:2000/login/")
			.then()
				.statusCode(200)
				.body(containsString("token:"));

			// Submit token
			given()
				.contentType(APPLICATION_X_WWW_FORM_URLENCODED)
				.formParam("token", getToken())
			.when()
				.post("http://localhost:2000/login/")
			.then()
				.statusCode(200)
				.body(containsString("This page has moved to"));

			// Access protected page
			given().when()
				.get("http://localhost:2000/")
			.then()
				.statusCode(200)
				.body(containsString("predic8.com"));

			// Logout
			given().when()
				.get("http://localhost:2000/login/logout")
			.then()
				.statusCode(200)
				.body(containsString("Username:"));
			// @formatter:on
		}
	}

	private String getToken() {
		return new OtpProvider().getNextCode("abcdefghijklmnop", System.currentTimeMillis());
	}
}
