/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport;

import static com.predic8.membrane.test.AssertUtils.assertContains;
import static com.predic8.membrane.test.AssertUtils.assertContainsNot;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.ContentTypeDetector.ContentType;
import com.predic8.membrane.core.util.HttpUtil;

@RunWith(Parameterized.class)
public class ExceptionHandlingTest {
		
	@Parameters
	public static List<Object[]> getPorts() {
		return Arrays.asList(new Object[][] { 
				{ true, ContentType.UNKNOWN },
				{ true, ContentType.SOAP },
				{ true, ContentType.JSON },
				{ true, ContentType.XML },
				{ false, ContentType.UNKNOWN },
				{ false, ContentType.SOAP },
				{ false, ContentType.JSON },
				{ false, ContentType.XML },
		});
	}

	private final boolean printStackTrace;
	private final ContentType contentType;

	public ExceptionHandlingTest(boolean printStackTrace, ContentType contentType) {
		this.printStackTrace = printStackTrace;
		this.contentType = contentType;
	}
	
	
	HttpRouter router;
	volatile long connectionHash = 0;
	
	@Before
	public void setUp() throws Exception {
		router = new HttpRouter();
		router.getTransport().setPrintStackTrace(printStackTrace);
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*",
				"*", ".*", getPort()), "", -1);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				throw new Exception("secret");
			}
		});
		router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		router.init();
	}

	@After
	public void tearDown() throws Exception {
		router.shutdown();
	}
	
	private int getPort() {
		return printStackTrace ? 3022 : 3023;
	}

	private static HttpClient hc;

	public static String getAndAssert(int expectedHttpStatusCode, HttpUriRequest request) throws ParseException, IOException {
		if (hc == null)
			hc = new DefaultHttpClient();
		HttpResponse res = hc.execute(request);
		try {
			assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " while fetching " + request.getURI());
		}
		HttpEntity entity = res.getEntity();
		return entity == null ? "" : EntityUtils.toString(entity);
	}
	
	private HttpUriRequest createRequest() throws UnsupportedEncodingException {
		String url = "http://localhost:" + getPort() + "/";
		HttpUriRequest get = null;
		switch (contentType) {
		case JSON:
			get = new HttpGet(url);
			get.addHeader(Header.CONTENT_TYPE, MimeType.APPLICATION_JSON_UTF8);
			break;
		case XML:
			get = new HttpPost(url);
			get.addHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
			((HttpPost)get).setEntity(new StringEntity("<foo />"));
			break;
		case SOAP:
			get = new HttpPost(url);
			get.addHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
			((HttpPost)get).setEntity(new StringEntity(HttpUtil.getFaultSOAPBody("", "")));
			break;
		default:
			get = new HttpGet(url);
			break;
		}
		return get;
	}
	
	private void checkResponseContentType(String response) {
		switch (contentType) {
		case JSON:
			assertTrue(response.startsWith("{"));
			return;
		case XML:
			assertTrue(response.startsWith("<"));
			assertContainsNot("Envelope", response);
			return;
		case SOAP:
			assertTrue(response.startsWith("<"));
			assertContains("Envelope", response);
			return;
		case UNKNOWN:
			return;
		}
		throw new RuntimeException("Unhandled contentType:" + contentType);
	}

	@Test
	public void testStackTraces() throws Exception {
		String response = getAndAssert(500, createRequest());
		Assert.assertEquals(printStackTrace, response.contains(".java:"));
		assertContains("secret", response);
		checkResponseContentType(response);
	}
	
}
