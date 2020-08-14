/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.MockInterceptor;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;

public class InterceptorInvocationTest {

	private HttpRouter router;

	List<String> backboneInterceptorNames;

	List<String> regularInterceptorNames;

	List<String> ruleInterceptorNames;

	List<String> interceptorSequence;

	@Before
	public void setUp() throws Exception {

		MockInterceptor.clear();

		ruleInterceptorNames = Arrays.asList(new String[] {"Rule 1", "Rule 2", "Rule 3"});

		regularInterceptorNames = Arrays.asList(new String[] {"TR Normal 1", "TR Normal 2", "TR Normal 3", "TR Normal 4" });

		router = createRouter();

		interceptorSequence = createInterceptorSequence();

	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}

	@Test
	public void testInterceptorSequence() throws Exception {
		callService();

		MockInterceptor.assertContent(
				interceptorSequence,
				getReverseList(interceptorSequence),
				Arrays.<String>asList());
	}

	private ServiceProxy createServiceProxy() {
		ServiceProxy rule = new ServiceProxy(new ServiceProxyKey("localhost", Request.METHOD_POST, "*", 4000), "thomas-bayer.com", 80);
		for (String label : ruleInterceptorNames) {
			rule.getInterceptors().add(new MockInterceptor(label));
		}
		return rule;
	}

	private void callService() throws HttpException, IOException {
		new HttpClient().executeMethod(createPostMethod());
	}

	private PostMethod createPostMethod() {
		PostMethod post = new PostMethod("http://localhost:4000/axis2/services/BLZService");
		post.setRequestEntity(new InputStreamRequestEntity(this.getClass().getResourceAsStream("/getBank.xml")));
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");
		return post;
	}

	private List<String> getReverseList(List<String> list) {
		List<String> res = new ArrayList<String>(list);
		Collections.reverse(res);
		return res;
	}

	private List<String> createInterceptorSequence() {
		List<String> sequense = new ArrayList<String>();
		sequense.addAll(regularInterceptorNames);
		sequense.addAll(ruleInterceptorNames);
		return sequense;
	}

	private HttpRouter createRouter() throws Exception {
		HttpRouter router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(createServiceProxy());
		addMockInterceptors(router, regularInterceptorNames);
		router.init();
		return router;
	}

	private void addMockInterceptors(HttpRouter router, List<String> labels) {
		for (String label : labels) {
			router.addUserFeatureInterceptor(new MockInterceptor(label));
		}
	}
}
