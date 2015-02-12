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
package com.predic8.membrane.integration;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager.RuleDefinitionSource;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.client.ProxyConfiguration;


public class ViaProxyTest {

	HttpRouter proxyRouter;
	HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		ProxyConfiguration proxy = new ProxyConfiguration();
		proxy.setHost("localhost");
		proxy.setPort(3128);

		proxyRouter = new HttpRouter(proxy);
		proxyRouter.getRuleManager().addProxy(new ProxyRule(new ProxyRuleKey(3128)), RuleDefinitionSource.MANUAL);
		proxyRouter.init();
		
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 4000), "thomas-bayer.com", 80));
		router.init();
	}
	
	@Test
	public void testPost() throws Exception {
		HttpClient client = new HttpClient();
		PostMethod post = new PostMethod("http://localhost:4000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");
		
		assertEquals(200, client.executeMethod(post));
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
		proxyRouter.shutdown();
	}
}
