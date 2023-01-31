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

package com.predic8.membrane.core.interceptor.authentication;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.*;
import com.predic8.membrane.core.rules.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class BasicAuthenticationInterceptorIntegrationTest {

	private static final HttpRouter router = new HttpRouter();

	@BeforeAll
	public static void setup() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3001), "thomas-bayer.com", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);

		BasicAuthenticationInterceptor interceptor = new BasicAuthenticationInterceptor();

		List<User> users = new ArrayList<>();
		users.add(new User("admin", "admin"));
		interceptor.setUsers(users );

		router.addUserFeatureInterceptor(interceptor);
		router.init();
	}

	@Test
	public void testDeny() throws Exception {
		disableHTTPAuthentication();
		getAndAssert(401, "http://localhost:3001/axis2/services/BLZService?wsdl");
	}

	@Test
	public void testAccept() throws Exception {
		setupHTTPAuthentication("localhost", 3001, "admin", "admin");
		getAndAssert200("http://localhost:3001/axis2/services/BLZService?wsdl");
	}

	@Test
	public void testHashedPassword() {
		List<User> users = new ArrayList<>();
		User user = new User("admin", "$6$12345678$jwCsYagMo/KNcTDqnrWL25Dy3AfAT5U94abA5a/iPFO.Cx2zAkMpPxZBNKY/P/xiRrCfCFDxdBp7pvNEMoBcr0");
		users.add(user);

		BasicAuthenticationInterceptor interceptor = new BasicAuthenticationInterceptor();
		StaticUserDataProvider provider = (StaticUserDataProvider) interceptor.getUserDataProvider();
		interceptor.setUsers(users);

		try {
			provider.verify(new User("admin","admin").getAttributes());
		}catch(Exception e){
			fail();
		}
	}

	@AfterAll
	public static void teardown() throws IOException {
		closeConnections();
		router.shutdown();
	}

}
