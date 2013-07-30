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
package com.predic8.membrane.core.http;

import static org.junit.Assert.assertTrue;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;

public class MethodTest {

	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 4000), "oio.de", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		router.init();
	}
	
	@Test
	public void testDELETE() throws Exception {
		HttpClient client = new HttpClient();
		
		DeleteMethod delete = new DeleteMethod("http://localhost:4000/method-test/");
		
		int status = client.executeMethod(delete);
		assertTrue(status < 400);
	}
		
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

}
