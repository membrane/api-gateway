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

import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.router.TestRouter;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static com.predic8.membrane.core.util.NetworkTestUtil.freePort;
import static com.predic8.membrane.core.util.text.TextUtil.isNullOrEmpty;
import static org.apache.commons.httpclient.HttpVersion.HTTP_1_1;
import static org.apache.http.params.CoreProtocolPNames.PROTOCOL_VERSION;
import static org.apache.http.params.CoreProtocolPNames.USE_EXPECT_CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

public class Http11Test {

	private static Router router;
	private static Router router2;
	private static int proxyPort;

    @BeforeAll
	public static void setUp() throws Exception {
		proxyPort = freePort();
        int backendPort = freePort();
		// freePort() closes its probe, so the OS may hand out the same port twice
		while (backendPort == proxyPort) {
			backendPort = freePort();
		}
		ServiceProxy proxy2 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", backendPort), null, 0);
		proxy2.getFlow().add(new SampleSoapServiceInterceptor());
		router2 = new TestRouter();
		router2.add(proxy2);
		router2.start();
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", proxyPort), "localhost", backendPort);
		router = new TestRouter();
		router.add(proxy);
		router.start();
	}

	@AfterAll
	public static void tearDown() {
		router2.stop();
		router.stop();
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
		PostMethod post = new PostMethod("http://localhost:%s/".formatted(proxyPort));
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
