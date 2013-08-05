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
package com.predic8.membrane.core.interceptor.acl;

import static com.predic8.membrane.test.AssertUtils.getAndAssert;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class AccessControlInterceptorTest {

	private static final String BASE_URL = "http://localhost:4000";

	private AccessControlInterceptor interceptor;
	
	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		
		interceptor = new AccessControlInterceptor();
		interceptor.setFile("classpath:/acl/acl.xml");
		
		Rule rule4000 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 4000), "oio.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule4000);
		router.addUserFeatureInterceptor(interceptor);
		router.init();
	}
	
	@Test
	public void testGetAccessControl() throws Exception {
		assertNotNull(interceptor.getAccessControl());
	}
	
	@Test
	public void testAuthorizedAccess() throws Exception {
		getAndAssert(200, BASE_URL + "/axis2/services/BLZService?wsdl");
	}
	
	@Test
	public void testUnauthorizedRequestUri() throws Exception {
		getAndAssert(403, BASE_URL + "/predic8/services/BLZService?wsdl"); 
	}
	
	@Test
	public void testUnauthorizedClient() throws Exception {
		getAndAssert(403, BASE_URL + "/crm/services/BLZService?wsdl"); 
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}
	
}
