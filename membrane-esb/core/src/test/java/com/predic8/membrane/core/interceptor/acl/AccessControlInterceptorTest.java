/* Copyright 2009 predic8 GmbH, www.predic8.com

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

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;
public class AccessControlInterceptorTest {

	private AccessControlInterceptor interceptor;
	
	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename("classpath:/acl/acl.xml");
		
		
		Rule rule4000 = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 4000), "oio.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyIfNew(rule4000);
		router.getTransport().getInterceptors().add(interceptor);
		interceptor.setRouter(router);
	}
	
	@Test
	public void testGetAccessControl() throws Exception {
		assertNotNull(interceptor.getAccessControl());
	}
	
	@Test
	public void testAuthorizedAccess() throws Exception {
		assertEquals(200, callService("/axis2/services/BLZService?wsdl"));
	}
	
	@Test
	public void testUnauthorizedRequestUri() throws Exception {
		assertEquals(403, callService("/predic8/services/BLZService?wsdl")); 
	}
	
	@Test
	public void testUnauthorizedClient() throws Exception {
		assertEquals(403, callService("/crm/services/BLZService?wsdl")); 
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
		Thread.sleep(200);
	}
	
	private int callService(String uri) throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		GetMethod get = new GetMethod("http://localhost:4000" + uri);
		return client.executeMethod(get);
	}
	
}
