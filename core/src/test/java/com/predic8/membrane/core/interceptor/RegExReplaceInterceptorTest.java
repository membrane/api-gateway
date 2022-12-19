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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.rules.Rule;

import java.util.regex.Pattern;

public class RegExReplaceInterceptorTest {

	private Router router;

	@Test
	public void testReplace() throws Exception {
		router = Router.init("src/test/resources/regex-monitor-beans.xml");
		Rule serverRule = new ServiceProxy(new ServiceProxyKey("localhost", "*", ".*", 3009), "www.predic8.de", 80);
		router.getRuleManager().addProxyAndOpenPortIfNew(serverRule);
		router.init();

		try {
			HttpClient client = new HttpClient();

			GetMethod method = new GetMethod("http://localhost:3009");
			method.setRequestHeader(Header.CONTENT_TYPE, MimeType.TEXT_XML_UTF8);
			method.setRequestHeader(Header.SOAP_ACTION, "");

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
		assertTrue(exc.getRequest().getBodyAsStringDecoded().equals("Membrane"));

		exc = new Request.Builder().body(example).header("Content-Type","application/octet-stream").buildExchange();
		regexp.handleRequest(exc);
		assertTrue(exc.getRequest().getBodyAsStringDecoded().equals(example));
	}
}
