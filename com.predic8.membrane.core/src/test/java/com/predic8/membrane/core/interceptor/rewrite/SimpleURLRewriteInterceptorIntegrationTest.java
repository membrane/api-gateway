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
package com.predic8.membrane.core.interceptor.rewrite;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.params.HttpProtocolParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;
public class SimpleURLRewriteInterceptorIntegrationTest {

	private static HttpRouter router;

	private SimpleURLRewriteInterceptor  interceptor; 
	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3007), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(rule);
		
		interceptor = new SimpleURLRewriteInterceptor();
		Map<String, String> mapping = new HashMap<String, String>();
		mapping.put("/blz-service?wsdl", "/axis2/services/BLZService?wsdl");
		interceptor.setMapping(mapping );
		
		router.getTransport().getInterceptors().add(0, interceptor);
	}

	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}

	@Test
	public void testRewriting() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		int status = client.executeMethod(getPostMethod());
	
	    assertEquals(200, status);
	}
	
	private PostMethod getPostMethod() {
		PostMethod post = new PostMethod("http://localhost:3007/blz-service?wsdl");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		return post;
	}

	
}
