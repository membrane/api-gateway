/* Copyright 2011, 2012, 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication;

import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.Base64.*;
import static java.util.List.*;
import static org.junit.jupiter.api.Assertions.*;

public class BasicAuthenticationInterceptorTest {

	static BasicAuthenticationInterceptor bai;

	@BeforeEach
	void setup() {
		bai = new BasicAuthenticationInterceptor();
		bai.setUsers(of(
				new UserConfig("admin", "secret"),
				new UserConfig("klara", "$6$jd3$AHVA4BVU0wTtVmF6ocQLSvxds455z0RKeNG/3Y0kF8C9AmAqyo8WBhEDpZ3JjO3k3lX/t5MB0NQrqGDpQyQf.1")
		));
		bai.init(new DefaultRouter());
	}

	@Test
	void deny() throws Exception {
		var exc = get("/foo").buildExchange();
		assertEquals(ABORT, bai.handleRequest(exc));
		assertEquals(401, exc.getResponse().getStatusCode());
	}

	@Test
	void accept() throws Exception {
		var exc = get("/foo").header(AUTHORIZATION, getAuthString("admin", "secret")).buildExchange();
		assertEquals(CONTINUE, bai.handleRequest(exc));
		assertNull(exc.getRequest().getHeader().getAuthorization());
	}

	@Test
	void hashedPassword() throws Exception {
		var exc = get("/foo").header(AUTHORIZATION, getAuthString("klara", "admin")).buildExchange();
		bai.setRemoveAuthorizationHeader(false);
		assertEquals(CONTINUE, bai.handleRequest(exc));
		assertNotNull(exc.getRequest().getHeader().getAuthorization());
	}

	private String getAuthString(String user, String password) {
		return "Basic " + getEncoder().encodeToString((user + ":" + password).getBytes());
	}
}
