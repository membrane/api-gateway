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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.MessageUtil;
public class AccessControlInterceptorTest {

	private AccessControlInterceptor interceptor;
	
	private Exchange exc;
	
	@Before
	public void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService?wsdl"));
		
		interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename("resources/acl.xml");
	}
	
	@Test
	public void testGetAccessControl() throws Exception {
		assertNotNull(interceptor.getAccessControl());
	}
	
	@Test
	public void testDefaultAccess() throws Exception {
		assertEquals(Outcome.ABORT, interceptor.handleRequest(exc));
	}
	
}
