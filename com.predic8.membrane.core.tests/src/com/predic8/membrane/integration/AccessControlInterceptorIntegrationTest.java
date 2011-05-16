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

import java.io.InputStream;
import java.net.InetAddress;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class AccessControlInterceptorIntegrationTest {

	public static final String FILE_WITH_VALID_SERVICE_PARAMS = "resources/acl/valid-resource.xml";
	
	public static final String FILE_WITH_PATH_MISMATCH = "resources/acl/path-mismatch.xml";
	
	public static final String FILE_WITH_CLIENT_MISMATCH = "resources/acl/client-mismatch.xml";
	
	public static final String FILE_CLIENTS_FROM_PREDIC8 = "resources/acl/clients-from-predic8.de.xml";
	
	private static HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 8000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
	}
	
	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
		Thread.sleep(200);
	}
	
	@Test
	public void testValidServiceFile() throws Exception {
		setInterceptor(FILE_WITH_VALID_SERVICE_PARAMS);
		
		HttpClient client = new HttpClient();
		
		PostMethod post = getBLZRequestMethod();
		assertEquals(200, client.executeMethod(post));
		
	}
	
	@Test
	public void testPathMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_PATH_MISMATCH);
		HttpClient client = new HttpClient();
		
		PostMethod post = getBLZRequestMethod();
		assertEquals(403, client.executeMethod(post));
	}
	
	@Test
	public void testClientsMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_CLIENT_MISMATCH);
		HttpClient client = new HttpClient();
		
		PostMethod post = getBLZRequestMethod();
		assertEquals(403, client.executeMethod(post));
	}

	private void setInterceptor(String fileName) {
		AccessControlInterceptor interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename(fileName);
		router.getTransport().getInterceptors().add(interceptor);
	}
	
	private PostMethod getBLZRequestMethod() {
		PostMethod post = new PostMethod("http://localhost:8000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "\"\"");
		return post;
	}
	
	@Test
	public void testGlobPattern() throws Exception {
		setInterceptor(FILE_CLIENTS_FROM_PREDIC8);
		
		HttpClient client = new HttpClient();
		HostConfiguration config = new HostConfiguration();
		InetAddress address = InetAddress.getByAddress(new byte[]{ (byte)192, (byte)168, (byte)2,  (byte)155 });
		
		config.setLocalAddress(address);
		client.setHostConfiguration(config);
		
		PostMethod post = getBLZRequestMethod();
		
		assertEquals(200, client.executeMethod(post));
	}
	
}
