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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class Http10Test {

	private static HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 3000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	@Test
	public void testPost() throws Exception {
		
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION  , HttpVersion.HTTP_1_0);
		
		PostMethod post = new PostMethod("http://localhost:3000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "\"\"");
		int status = client.executeMethod(post);
		assertEquals(200, status);
		String response = post.getResponseBodyAsString();
		assertNotNull(response);
		assertTrue(response.length() > 0);
	}
	

	@Test
	public void testMultiplePost() throws Exception {
		
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION  , HttpVersion.HTTP_1_0);
		
		PostMethod post = new PostMethod("http://localhost:3000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "\"\"");
		
		for (int i = 0; i < 100; i ++) {
			System.out.println("Iteration: " + i);
			int status = client.executeMethod(post);
			assertEquals(200, status);
			String response = post.getResponseBodyAsString();
			assertNotNull(response);
			assertTrue(response.length() > 0);
		}
		
	}
	
	
}
