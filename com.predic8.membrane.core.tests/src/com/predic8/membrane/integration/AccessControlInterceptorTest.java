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

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;

import com.predic8.membrane.core.Configuration;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class AccessControlInterceptorTest extends TestCase {

	public static final String FILE_WITH_VALID_SERVICE_PARAMS = "resources/acl-valid-service.xml";
	
	public static final String FILE_WITH_PATH_MISMATCH = "resources/acl-path-mismatch.xml";
	
	public static final String FILE_WITH_CLIENT_MISMATCH = "resources/acl-client-mismatch.xml";
	
	private static HttpRouter router;
	
	@Override
	protected void setUp() throws Exception {
		Rule rule = new ForwardingRule(new ForwardingRuleKey("localhost", "POST", ".*", 8000), "thomas-bayer.com", "80");
		router = new HttpRouter();
		router.getConfigurationManager().setConfiguration(new Configuration());
		router.getRuleManager().addRuleIfNew(rule);
		
		router.getTransport().closeAll();
		router.getTransport().openPort(8000);
	}
	
	@Override
	protected void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	public void testValidServiceFile() throws Exception {
		setInterceptor(FILE_WITH_VALID_SERVICE_PARAMS);
		
		HttpClient client = new HttpClient();
		
		PostMethod post = getPostMethod();
		assertEquals(200, client.executeMethod(post));	
	}
	
	public void testPathMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_PATH_MISMATCH);
		HttpClient client = new HttpClient();
		
		PostMethod post = getPostMethod();
		assertEquals(403, client.executeMethod(post));
	}
	
	public void testClientsMismatchFile() throws Exception {
		setInterceptor(FILE_WITH_CLIENT_MISMATCH);
		HttpClient client = new HttpClient();
		
		PostMethod post = getPostMethod();
		assertEquals(403, client.executeMethod(post));
	}

	private void setInterceptor(String fileName) {
		AccessControlInterceptor interceptor = new AccessControlInterceptor();
		interceptor.setAclFilename(fileName);
		router.getTransport().getInterceptors().add(interceptor);
	}
	
	
	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:8000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader("Content-Type", "text/xml;charset=UTF-8");
		post.setRequestHeader("SOAPAction", "\"\"");
		return post;
	}
	
}
