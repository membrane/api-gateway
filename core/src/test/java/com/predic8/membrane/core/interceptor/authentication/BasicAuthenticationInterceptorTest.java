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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.util.Base64.getEncoder;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BasicAuthenticationInterceptorTest {

	static BasicAuthenticationInterceptor bai;

	@BeforeAll
	public static void setup() throws Exception {
		bai = new BasicAuthenticationInterceptor();
		bai.setUsers(of(
				new User("admin", "admin"),
				new User("admin", "$6$12345678$jwCsYagMo/KNcTDqnrWL25Dy3AfAT5U94abA5a/iPFO.Cx2zAkMpPxZBNKY/P/xiRrCfCFDxdBp7pvNEMoBcr0")
		));
	}

	@Test
	public void testDeny() throws Exception {
		Exchange exc = new Request.Builder().buildExchange();
		assertEquals(ABORT, bai.handleRequest(exc));
		assertEquals(401, exc.getResponse().getStatusCode());
	}

	@Test
	public void testAccept() throws Exception {
		Exchange exc = new Request.Builder().header(AUTHORIZATION, getAuthString("admin", "admin")).buildExchange();
		assertEquals(CONTINUE, bai.handleRequest(exc));
	}

	@Test
	public void testHashedPassword() throws Exception {
		Exchange exc = new Request.Builder().header(AUTHORIZATION, getAuthString("admin", "admin")).buildExchange();
		StaticUserDataProvider p = (StaticUserDataProvider) bai.getUserDataProvider();
		assertEquals(CONTINUE, bai.handleRequest(exc));
		p.verify(new User("admin","admin").getAttributes());
	}

	private String getAuthString(String user, String password) {
		return "Basic " + getEncoder().encodeToString((user + ":" + password).getBytes());
	}
}
