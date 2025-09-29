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
package com.predic8.membrane.core.http;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.proxies.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.util.NetworkUtil.*;
import static com.predic8.membrane.core.util.TextUtil.*;
import static org.apache.commons.httpclient.HttpVersion.HTTP_1_1;
import static org.apache.http.params.CoreProtocolPNames.*;
import static org.junit.jupiter.api.Assertions.*;

public class Http11Test {

	private static HttpRouter router;
	private static HttpRouter router2;
	private static int port4k;

    @BeforeAll
	public static void setUp() throws Exception {
		port4k = getFreePortEqualAbove(4000);
        int port5k = getFreePortEqualAbove(5000);
		ServiceProxy proxy2 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port5k), null, 0);
		proxy2.getFlow().add(new SampleSoapServiceInterceptor());
		router2 = new HttpRouter();
		router2.getRuleManager().addProxyAndOpenPortIfNew(proxy2);
		router2.init();
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", port4k), "localhost", port5k);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy);
		router.init();
	}

	@AfterAll
	public static void tearDown() {
		router2.shutdown();
		router.shutdown();
	}

	/**
	 * Note that "Read timed out" indicates incorrect server behavior. The
	 * socket timeout is set on the client to avoid fallback mentioned in
	 * RFC2616 section 8.2.3 ("indefinite period").
	 */
	public static void initExpect100ContinueWithFastFail(HttpClient client) {
		client.getParams().setParameter(PROTOCOL_VERSION, HTTP_1_1);
		client.getParams().setParameter(USE_EXPECT_CONTINUE, true);
		client.getParams().setParameter("http.method.retry-handler", (HttpMethodRetryHandler) (arg0, arg1, arg2) -> false);
		client.getParams().setParameter("http.socket.timeout", 7000);
	}

	private void testPost(boolean useExpect100Continue) throws Exception {
		HttpClient client = new HttpClient();
		if (useExpect100Continue)
			initExpect100ContinueWithFastFail(client);
		PostMethod post = new PostMethod("http://localhost:%s/".formatted(port4k));
		InputStream stream = this.getClass().getResourceAsStream("/get-city.xml");

		InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity);
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "");

		int status = client.executeMethod(post);
		assertEquals(200, status);
		assertTrue(post.getResponseBodyAsString().contains("population"));
		assertNotNull(post.getResponseBodyAsString());
		assertFalse(isNullOrEmpty(post.getResponseBodyAsString()));
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
