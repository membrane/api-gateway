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

import static com.predic8.membrane.core.Constants.WSDL_HTTP_NS;
import static com.predic8.membrane.core.Constants.WSDL_SOAP11_NS;
import static com.predic8.membrane.core.Constants.WSDL_SOAP12_NS;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;
import com.predic8.membrane.core.util.MessageUtil;

public class WSDLInterceptorTest {

	private static final QName ADDRESS_SOAP11 = new QName(WSDL_SOAP11_NS,
			"address");
	private static final QName ADDRESS_SOAP12 = new QName(WSDL_SOAP12_NS,
			"address");
	private static final QName ADDRESS_HTTP = new QName(WSDL_HTTP_NS, "address");

	private Exchange exc;

	private WSDLInterceptor interceptor;

	@Before
	public void setUp() throws Exception {
		exc = new Exchange(new FakeHttpHandler(3011));
		exc.setRequest(MessageUtil
				.getGetRequest("/axis2/services/BLZService?wsdl"));
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/blz-service.wsdl");
		Response okResponse = Response.ok()
				.contentType("text/xml; charset=utf-8")
				.body(resourceAsStream, true)
				.build();
		exc.setResponse(okResponse);

		exc.setOriginalHostHeader("thomas-bayer.com:80");

		interceptor = new WSDLInterceptor();
	}

	@Test
	public void testProtocolSet() throws Exception {
		interceptor.setProtocol("https");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		XMLEventReader parser = getParser();

		// System.out.println("parser is: " + parser);

		StartElement element = getElement(parser, ADDRESS_SOAP11);
		String locationAttr = getLocationAttributeFor(element);
		// System.out.println("location attribute is: " + locationAttr);
		assertTrue(locationAttr.startsWith("https://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), ADDRESS_SOAP12)).startsWith("https://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), ADDRESS_HTTP)).startsWith("https://"));
	}

	@Test
	public void testProtocolDefault() throws Exception {
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		assertTrue(getLocationAttributeFor(
				getElement(getParser(), ADDRESS_SOAP11)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), ADDRESS_SOAP12)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), ADDRESS_HTTP)).startsWith("http://"));
	}

	@Test
	public void testPortEmpty() throws Exception {
		interceptor.setPort("");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		assertFalse(matchSoap11(".*:80.*"));
		assertFalse(matchSoap12(".*:80.*"));
		assertFalse(matchHttp(".*:80.*"));
	}

	@Test
	public void testPortDefault() throws Exception {
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		assertTrue(matchSoap11(".*:3011.*"));
		assertTrue(matchSoap12(".*:3011.*"));
		assertTrue(matchHttp(".*:3011.*"));
	}

	@Test
	public void testPortSet() throws Exception {
		interceptor.setPort("2000");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		assertTrue(matchSoap11(".*:2000.*"));
		assertTrue(matchSoap12(".*:2000.*"));
		assertTrue(matchHttp(".*:2000.*"));
	}

	@Test
	public void testHostSet() throws Exception {
		interceptor.setHost("abc.com");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		assertTrue(matchSoap11("http://abc.com.*"));
		assertTrue(matchSoap12("http://abc.com.*"));
		assertTrue(matchHttp("http://abc.com.*"));
	}

	@Test
	public void testHostDefault() throws Exception {
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);

		assertTrue(matchSoap11("http://thomas-bayer.com.*"));
		assertTrue(matchSoap12("http://thomas-bayer.com.*"));
		assertTrue(matchHttp("http://thomas-bayer.com.*"));
	}

	private XMLEventReader getParser() throws Exception {
		return XMLInputFactory.newInstance().createXMLEventReader(
				new InputStreamReader(exc.getResponse().getBodyAsStream(), exc
						.getResponse().getCharset()));
	}

	private String getLocationAttributeFor(StartElement element) {
		return element.getAttributeByName(
				new QName(XMLConstants.NULL_NS_URI, "location")).getValue();
	}

	private boolean matchSoap12(String pattern) throws XMLStreamException,
			Exception {
		return match(pattern, ADDRESS_SOAP12);
	}

	private boolean matchSoap11(String pattern) throws XMLStreamException,
			Exception {
		return match(pattern, ADDRESS_SOAP11);
	}

	private boolean matchHttp(String pattern) throws XMLStreamException,
			Exception {
		return match(pattern, ADDRESS_HTTP);
	}

	private boolean match(String pattern, QName addressElementName)
			throws XMLStreamException, Exception {
		return Pattern
				.compile(pattern)
				.matcher(
						getLocationAttributeFor(getElement(getParser(),
								addressElementName))).matches();
	}

	private StartElement getElement(XMLEventReader parser, QName qName)
			throws XMLStreamException {
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();

			if (event.isStartElement()) {
				if (event.asStartElement().getName().equals(qName)) {
					return event.asStartElement();
				}
			}
		}
		throw new RuntimeException("element " + qName
				+ " not found in response");
	}

}
