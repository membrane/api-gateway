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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.ContentTypeDetector.ContentType;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.SOAPUtil.FaultCode.*;
import static com.predic8.membrane.core.util.SOAPUtil.*;
import static com.predic8.membrane.test.AssertUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExceptionHandlingTest {

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

	HttpRouter router;

	public void setUp(boolean printStackTrace) throws Exception {
		router = new HttpRouter();
		router.getTransport().setPrintStackTrace(printStackTrace);
		ServiceProxy sp2 = new ServiceProxy(new ServiceProxyKey("*",
				"*", ".*", getPort(printStackTrace)), "", -1);
		sp2.getInterceptors().add(new AbstractInterceptor(){
			@Override
			public Outcome handleRequest(Exchange exc) throws Exception {
				throw new Exception("secret");
			}
		});
		router.getRuleManager().addProxyAndOpenPortIfNew(sp2);
		router.init();
	}

	@AfterEach
	public void tearDown() throws Exception {
		router.shutdown();
	}

	private int getPort(boolean printStackTrace) {
		return printStackTrace ? 3022 : 3023;
	}

	private static HttpClient hc;

	public static String getAndAssert(int expectedHttpStatusCode, HttpUriRequest request) throws ParseException, IOException {
		if (hc == null)
			hc = HttpClientBuilder.create().build();
		HttpResponse res = hc.execute(request);
		try {
			assertEquals(expectedHttpStatusCode, res.getStatusLine().getStatusCode());
		} catch (AssertionError e) {
			throw new AssertionError(e.getMessage() + " while fetching " + request.getURI());
		}
		HttpEntity entity = res.getEntity();
		return entity == null ? "" : EntityUtils.toString(entity);
	}

	private HttpUriRequest createRequest(boolean printStackTrace, ContentType contentType) throws UnsupportedEncodingException {
		String url = "http://localhost:" + getPort(printStackTrace) + "/";
		HttpUriRequest get;
		switch (contentType) {
		case JSON:
			get = new HttpGet(url);
			get.addHeader(CONTENT_TYPE, MimeType.APPLICATION_JSON_UTF8);
			return get;
		case XML:
			get = new HttpPost(url);
			get.addHeader(CONTENT_TYPE, TEXT_XML_UTF8);
			((HttpPost)get).setEntity(new StringEntity("<foo />"));
			return get;
		case SOAP:
			get = new HttpPost(url);
			get.addHeader(CONTENT_TYPE, TEXT_XML_UTF8);
			((HttpPost)get).setEntity(new StringEntity(getFaultSOAP11Body(Server,"dummy", "no detail")));
			return get;
		default:
			return new HttpGet(url);
		}
	}

	private void checkResponseContentType(ContentType contentType, String response) {
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

	@ParameterizedTest
	@MethodSource("getPorts")
	public void testStackTraces(boolean printStackTrace, ContentType contentType) throws Exception {
		setUp(printStackTrace);

		String response = getAndAssert(500, createRequest(printStackTrace, contentType));
		assertEquals(printStackTrace, response.contains(".java:"));
		assertContains("secret", response);
		checkResponseContentType(contentType, response);
	}

}
