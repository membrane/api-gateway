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
package com.predic8.membrane.core.interceptor.rest;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.xml.*;
import com.predic8.membrane.core.util.MessageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class HTTP2XMLInterceptorTest {

	private Exchange exc;

	private HTTP2XMLInterceptor interceptor = new HTTP2XMLInterceptor();

	XPath xpath = XPathFactory.newInstance().newXPath();

	@BeforeEach
	protected void setUp() throws Exception {
		exc = new Exchange(null);
		exc.setRequest(MessageUtil.getGetRequest("http://localhost/axis2/services/BLZService?wsdl"));
		exc.getRequest().setUri("http://localhost:3011/manager/person?vorname=jim&nachname=panse");
		exc.getRequest().setMethod("POST");
		exc.getRequest().setVersion("1.1");
		exc.getRequest().getHeader().add("Host", "localhost:3011");
	}

	@Test
	public void testRequest() throws Exception {

		interceptor.handleRequest(exc);

		assertXPath("local-name(/*)", "request");
		assertXPath("/*/@method", "POST");
		assertXPath("/*/@http-version", "1.1");
		assertXPath("/*/uri/@value", "http://localhost:3011/manager/person?vorname=jim&nachname=panse");
		assertXPath("/*/uri/path/component[1]", "manager");
		assertXPath("/*/uri/path/component[2]", "person");
		assertXPath("/*/uri/query/param[@name='vorname']", "jim");
		assertXPath("/*/uri/query/param[@name='nachname']", "panse");
		assertXPath("/*/headers/header[@name='Host']", "localhost:3011");
	}

	@Test
	public void parseXML() throws Exception {
		String xml = "<request method='POST' http-version='1.1'><uri value='http://localhost:3011/manager/person?vorname=jim&amp;nachname=panse'><host>localhost</host><port>3011</port><path><component>manager</component><component>person</component></path><query><param name='vorname'>jim</param><param name='nachname'>panse</param></query></uri></request>";

		XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
		r.next(); //skip DocumentNode
		Request req = new Request();
		req.parse(r);

		assertEquals("POST", req.getMethod());
		assertEquals("1.1", req.getHttpVersion());
		assertURI(req.getUri());
	}

	private void assertURI(URI uri) {
		assertEquals("http://localhost:3011/manager/person?vorname=jim&nachname=panse", uri.getValue());
		assertEquals("localhost", uri.getHost());
		assertEquals(3011, uri.getPort());
		assertPath(uri.getPath());
		assertQuery(uri.getQuery());
	}

	private void assertQuery(Query query) {
		assertParam("vorname", "jim", query.getParams().get(0));
		assertParam("nachname", "panse", query.getParams().get(1));
	}

	private void assertParam(String name, String value, Param param) {
		assertEquals(name, param.getName());
		assertEquals(value, param.getValue());
	}

	private void assertPath(Path path) {
		assertEquals("manager", path.getComponents().get(0).getValue());
		assertEquals("person", path.getComponents().get(1).getValue());
	}

	private void assertXPath(String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc.getRequest().getBodyAsStream())));
	}


}
