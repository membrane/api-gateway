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
package com.predic8.membrane.integration;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;


public class ProxyRuleTest extends TestCase {

	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(new ProxyRule(new ProxyRuleKey(3128)));
		router.getTransport().openPort(3128);
	}
	
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testPost() throws Exception {
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod("https://predic8.com");
		assertEquals(200, client.executeMethod(get));

		System.out.println(get.getResponseBodyAsString());
	}
	
}
