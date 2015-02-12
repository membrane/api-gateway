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

import static com.predic8.membrane.core.util.TextUtil.isNullOrEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
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

public class Http11Test {

	private HttpRouter router;
	
	@Before
	public void setUp() throws Exception {
		Rule rule = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 4000), "thomas-bayer.com", 80);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(rule);
		router.init();
	}
	
	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}
	
	/**
	 * Note that "Read timed out" indicates incorrect server behavior. The
	 * socket timeout is set on the client to avoid fallback mentioned in
	 * RFC2616 section 8.2.3 ("indefinite period").
	 */
	public static void initExpect100ContinueWithFastFail(HttpClient client) {
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		client.getParams().setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, true);
		client.getParams().setParameter("http.method.retry-handler", new HttpMethodRetryHandler() {
			@Override
			public boolean retryMethod(HttpMethod arg0, IOException arg1, int arg2) {
				return false;
			}
		});
		client.getParams().setParameter("http.socket.timeout", 7000);
	}
	
	private void testPost(boolean useExpect100Continue) throws Exception {
		HttpClient client = new HttpClient();
		if (useExpect100Continue)
			initExpect100ContinueWithFastFail(client);
		PostMethod post = new PostMethod("http://localhost:4000/axis2/services/BLZService");
		InputStream stream = this.getClass().getResourceAsStream("/getBank.xml");
		
		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity); 
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");
		
		int status = client.executeMethod(post); // also see comment on initExpect100ContinueWithFastFail()
		assertEquals(200, status);
		assertNotNull(post.getResponseBodyAsString());
		assertFalse(isNullOrEmpty(post.getResponseBodyAsString()));
		//System.out.println(post.getResponseBodyAsString());
	}
	
	@Test
	public void testPost() throws Exception {
		testPost(false);
	}
	
	@Test
	public void testExpect100Continue() throws Exception {
		testPost(true);
	}
}
