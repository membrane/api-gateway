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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor.User;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.test.AssertUtils;

public class BasicAuthenticationInterceptorIntegrationTest {

	private HttpRouter router = new HttpRouter();

	@Before
	public void setup() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3001), "thomas-bayer.com", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		
		BasicAuthenticationInterceptor interceptor = new BasicAuthenticationInterceptor();
		
		List<User> users = new ArrayList<BasicAuthenticationInterceptor.User>();
		users.add(new BasicAuthenticationInterceptor.User("admin", "admin"));
		interceptor.setUsers(users );
		
		router.addUserFeatureInterceptor(interceptor);
		router.init();
	}

	@Test
	public void testDeny() throws Exception {
		AssertUtils.getAndAssert(401, "http://localhost:3001/axis2/services/BLZService?wsdl");
	}
	
	@Test
	public void testAccept() throws Exception {
		AssertUtils.setupHTTPAuthentication("localhost", 3001, "admin", "admin");
		AssertUtils.getAndAssert200("http://localhost:3001/axis2/services/BLZService?wsdl");
	}

	@After
	public void teardown() throws IOException {
		AssertUtils.closeConnections();
		router.shutdown();
	}
	
}
