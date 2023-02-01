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
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.rules.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.junit.jupiter.api.*;

import java.util.regex.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class RegExReplaceInterceptorTest {

	private RegExReplaceInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new RegExReplaceInterceptor();
		interceptor.setRegex("\\bb.*?\\b");
		interceptor.setReplace("boo");
	}

	@Test
	public void testHandleRequest() throws Exception {
		Exchange exc = new Request.Builder().contentType(TEXT_PLAIN).body("foo bar baz").buildExchange();
		interceptor.handleRequest(exc);
		assertEquals("foo boo boo",exc.getRequest().getBodyAsStringDecoded());
	}

	@Test
	public void testHandleResponse() throws Exception {
		Exchange exc = new Request.Builder().buildExchange();
		exc.setResponse(Response.ok().contentType(TEXT_PLAIN).body("foo bar baz").build());
		interceptor.handleResponse(exc);
		assertEquals("foo boo boo",exc.getResponse().getBodyAsStringDecoded());
	}

	@Test
	public void testReplace() throws Exception {
		Router router = Router.init("src/test/resources/regex-monitor-beans.xml");
		Rule serverRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3009), "www.predic8.de", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(serverRule);
		router.init();

		try {
			HttpClient client = new HttpClient();

			GetMethod method = new GetMethod("http://localhost:3009");
			method.setRequestHeader(CONTENT_TYPE, TEXT_XML_UTF8);
			method.setRequestHeader(SOAP_ACTION, "");

			assertEquals(200, client.executeMethod(method));

			assertTrue(new String(method.getResponseBody()).contains("Membrane RegEx Replacement Is Cool"));
		}finally {
			router.shutdown();
		}
	}

	@Test
	public void testReplaceBinary() throws Exception {
		String example = "Hello";

		RegExReplaceInterceptor regexp = new RegExReplaceInterceptor();
		regexp.setRegex(Pattern.quote(example));
		regexp.setReplace("Membrane");

		Exchange exc = new Request.Builder().body(example).header("Content-Type","text/plain").buildExchange();

		regexp.handleRequest(exc);
		assertEquals("Membrane", exc.getRequest().getBodyAsStringDecoded());

		exc = new Request.Builder().body(example).header("Content-Type","application/octet-stream").buildExchange();
		regexp.handleRequest(exc);
		assertEquals(exc.getRequest().getBodyAsStringDecoded(), example);
	}

}
