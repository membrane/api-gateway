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

package com.predic8.membrane.integration;

import static org.junit.Assert.assertEquals;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchangestore.MemoryExchangeStore;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;

public class ProxySSLConnectionMethodTest {

	private Router router;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		router.setExchangeStore(new MemoryExchangeStore());
		router.getRuleManager().addProxyAndOpenPortIfNew(new ProxyRule(new ProxyRuleKey(3128)));
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void testSSLConnectionMethod() throws Exception {
		HttpClient client = new HttpClient();
		client.getHostConfiguration().setProxy("localhost", 3128);
	
		PostMethod post = new PostMethod("https://predic8.com/svn/membrane/monitor/");
		client.executeMethod(post);
		assertEquals("Subversion Repositories", post.getAuthenticationRealm());
	}
	
}
