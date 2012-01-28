/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.*;
import org.junit.*;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.interceptor.MockInterceptor;
import com.predic8.membrane.core.rules.*;

public class ServiceInvocationTest {

	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {		
		router = createRouter();
		MockInterceptor.reqLabels.clear();
		MockInterceptor.respLabels.clear();
	}

	@Test
	public void testInterceptorSequence() throws Exception {
		callService();
		
		assertEquals(Arrays.asList(new String[] {"process", "log", "transport-log" }), MockInterceptor.reqLabels);
		assertEquals(Arrays.asList(new String[] {"transport-log", "log", "process"  }), MockInterceptor.respLabels);
	}


	@After
	public void tearDown() throws Exception {
		router.getTransport().closeAll();
	}
	
	private ServiceProxy createFirstRule() {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost", Request.METHOD_POST, "*", 3016), "thomas-bayer.com", 80);
		rule.setTargetURL("service:log");
		rule.getInterceptors().add(new MockInterceptor("process"));
		return rule;
	}
	
	private ServiceProxy createServiceRule() {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost","*", "*", 3012), "thomas-bayer.com", 80);
		rule.setName("log");
		rule.getInterceptors().add(new MockInterceptor("log"));
		return rule;
	}
	
	private void callService() throws HttpException, IOException {
		new HttpClient().executeMethod(createPostMethod());
	}
	
	private PostMethod createPostMethod() {
		PostMethod post = new PostMethod("http://localhost:3016/axis2/services/BLZService?wsdl");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml"))); 
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");
		return post;
	}
	
	private HttpRouter createRouter() throws IOException {
		HttpRouter router = new HttpRouter();
		router.getRuleManager().addRuleIfNew(createFirstRule());
		router.getRuleManager().addRuleIfNew(createServiceRule());
		router.getTransport().getInterceptors().add(router.getTransport().getInterceptors().size()-1, new MockInterceptor("transport-log"));
		return router;
	}

}
