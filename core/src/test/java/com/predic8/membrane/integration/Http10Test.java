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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.soap.*;
import com.predic8.membrane.core.proxies.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.http.params.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
public class Http10Test {
    private static HttpRouter router;
	private static HttpRouter router2;

	@BeforeAll
	public static void setUp() throws Exception {
		ServiceProxy proxy2 = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 2000), null, 0);
		proxy2.getInterceptors().add(new SampleSoapServiceInterceptor());
        router2 = new HttpRouter();
		router2.getRuleManager().addProxyAndOpenPortIfNew(proxy2);
		router2.init();
		ServiceProxy proxy = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3000), "localhost", 2000);
		router = new HttpRouter();
		router.getRuleManager().addProxyAndOpenPortIfNew(proxy);
		router.init();
	}

	@AfterAll
	public static void tearDown() {
		router2.shutdown();
		router.shutdown();
	}

	@Test
	public void testPost() throws Exception {

		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);

		PostMethod post = new PostMethod("http://localhost:3000/");
		InputStream stream = this.getClass().getResourceAsStream("/get-city.xml");


        assert stream != null;
        InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity);
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "\"\"");
		int status = client.executeMethod(post);
		assertTrue(post.getResponseBodyAsString().contains("population"));
		assertEquals(200, status);
		assertEquals("HTTP/1.1", post.getStatusLine().getHttpVersion());

		String response = post.getResponseBodyAsString();
		assertNotNull(response);
        assertFalse(response.isEmpty());
	}


	@Test
	public void testMultiplePost() throws Exception {
		HttpClient client = new HttpClient();
		client.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);

		PostMethod post = new PostMethod("http://localhost:3000/");
		InputStream stream = this.getClass().getResourceAsStream("/get-city.xml");


        assert stream != null;
        InputStreamRequestEntity entity = new InputStreamRequestEntity(stream);
		post.setRequestEntity(entity);
		post.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
		post.setRequestHeader(Header.SOAP_ACTION, "\"\"");

		for (int i = 0; i < 100; i ++) {
			int status = client.executeMethod(post);
			assertTrue(post.getResponseBodyAsString().contains("population"));
			assertEquals(200, status);
			String response = post.getResponseBodyAsString();
			assertNotNull(response);
            assertFalse(response.isEmpty());
		}

	}


}
