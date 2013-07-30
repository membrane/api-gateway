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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;
import com.predic8.membrane.core.util.MessageUtil;

public class WADLInterceptorTest {

	NamespaceContext nsCtx = new NamespaceContext() {
		public String getNamespaceURI(String prefix) {
			if (prefix.equals("wadl")) {
				return "http://wadl.dev.java.net/2009/02";
			}
			return null;
		}

		// Dummy implementation - not used!
		public Iterator<?> getPrefixes(String val) {
			return null;
		}

		// Dummy implemenation - not used!
		public String getPrefix(String uri) {
			return null;
		}
	};

	private XPath xpath = XPathFactory.newInstance().newXPath();
	private WADLInterceptor interceptor;

	@Before
	public void setUp() throws Exception {

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
				"https://abc.de:443/search/V1/");

		assertAttribute(exc, "//wadl:resource/@path", "newsSearch");

		assertAttribute(exc, "//wadl:grammars/wadl:include[1]/@href",
				"https://abc.de:443/search.xsd");

		assertAttribute(exc, "//wadl:grammars/wadl:include[2]/@href",
				"https://abc.de:443/error/Error.xsd");
	}

	private void assertAttribute(Exchange exc, String xpathExpr, String expected)
			throws Exception {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc
				.getResponse().getBodyAsStream())));
	}

	private Exchange getExchange() throws IOException {
		Exchange exc = new Exchange(new FakeHttpHandler(3011));
		exc.setRequest(MessageUtil.getGetRequest("/search?wadl"));
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/wadls/search.wadl");
		Response okResponse = Response.ok()
				.contentType("text/xml; charset=utf-8")
				.body(resourceAsStream, true)
				.build();
		exc.setResponse(okResponse);

		exc.setOriginalHostHeader("thomas-bayer.com:80");

		return exc;
	}

}
