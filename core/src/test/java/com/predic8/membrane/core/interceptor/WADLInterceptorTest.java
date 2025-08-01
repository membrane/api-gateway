/* Copyright 2010, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;

import javax.xml.namespace.*;
import javax.xml.xpath.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

public class WADLInterceptorTest {

	static final NamespaceContext nsCtx = new NamespaceContext() {
		public String getNamespaceURI(String prefix) {
			if (prefix.equals("wadl")) {
				return "http://wadl.dev.java.net/2009/02";
			}
			return null;
		}

		// Dummy implementation - not used!
		public Iterator<String> getPrefixes(String val) {
			return null;
		}

		// Dummy implemenation - not used!
		public String getPrefix(String uri) {
			return null;
		}
	};

	private static final XPath xpath = XPathFactory.newInstance().newXPath();
	private static WADLInterceptor interceptor;

	@BeforeAll
	public static void setUp() throws Exception {

		xpath.setNamespaceContext(nsCtx);

		interceptor = new WADLInterceptor();
	}

	@Test
	public void testDefaultSettings() throws Exception {
		Exchange exc = getExchange();

		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		assertAttribute(exc, "//wadl:resources/@base",
				"http://thomas-bayer.com:3011/search/V1/");

		assertAttribute(exc, "//wadl:resource/@path", "newsSearch");

		assertAttribute(exc, "//wadl:grammars/wadl:include[1]/@href",
				"http://thomas-bayer.com:3011/search.xsd");

		assertAttribute(exc, "//wadl:grammars/wadl:include[2]/@href",
				"http://thomas-bayer.com:3011/error/Error.xsd");

	}

	@Test
	public void testProtocolHostAndPort() throws Exception {
		Exchange exc = getExchange();

		interceptor.setPort("443");
		interceptor.setProtocol("https");
		interceptor.setHost("abc.de");

		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		assertAttribute(exc, "//wadl:resources/@base",
				"https://abc.de/search/V1/");

		assertAttribute(exc, "//wadl:resource/@path", "newsSearch");

		assertAttribute(exc, "//wadl:grammars/wadl:include[1]/@href",
				"https://abc.de/search.xsd");

		assertAttribute(exc, "//wadl:grammars/wadl:include[2]/@href",
				"https://abc.de/error/Error.xsd");
	}

	private void assertAttribute(Exchange exc, String xpathExpr, String expected)
			throws Exception {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc
				.getResponse().getBodyAsStream())));
	}

	private Exchange getExchange() throws URISyntaxException {
		Exchange exc = new Exchange(new FakeHttpHandler(3011));
		exc.setRequest(get("/search?wadl").build());
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/wadls/search.wadl");
		exc.setResponse(Response.ok()
                .contentType("text/xml; charset=utf-8")
                .body(resourceAsStream, true)
                .build());
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		return exc;
	}

}
