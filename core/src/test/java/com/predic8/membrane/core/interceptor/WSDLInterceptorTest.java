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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import javax.xml.*;
import javax.xml.namespace.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.*;

public class WSDLInterceptorTest {

	private Exchange exc;

	private WSDLInterceptor interceptor;

	@BeforeEach
	public void setUp() throws Exception {
		exc = new Exchange(new FakeHttpHandler(3011));
		exc.setRequest(new Request.Builder().get("/axis2/services/BLZService?wsdl").build());
        exc.setResponse(ok()
				.contentType("text/xml; charset=utf-8")
				.body(WSDLInterceptorTest.class.getResourceAsStream("/blz-service.wsdl"), true)
				.build());

		exc.setOriginalHostHeader("thomas-bayer.com:80");

		interceptor = new WSDLInterceptor();
	}

	/**
	 * Tests rewrite from:
	 * <soap:address location="http://.."/>
	 * To:
	 * <soap:address location="https://.."/>
	 *
	 */
	@Test
	void testProtocolSet() throws Exception {
		interceptor.setProtocol("https");
		assertEquals(CONTINUE, interceptor.handleResponse(exc));

		XMLEventReader parser = getParser();

        assertTrue(getLocationAttributeFor(getElement(parser, WSDL11_ADDRESS_SOAP11)).startsWith("https://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), WSDL11_ADDRESS_SOAP12)).startsWith("https://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), WSDL11_ADDRESS_HTTP)).startsWith("https://"));
	}

	@Test
	void testProtocolDefault() throws Exception {
		assertEquals(CONTINUE, interceptor.handleResponse(exc));

		assertTrue(getLocationAttributeFor(
				getElement(getParser(), WSDL11_ADDRESS_SOAP11)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), WSDL11_ADDRESS_SOAP12)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(
				getElement(getParser(), WSDL11_ADDRESS_HTTP)).startsWith("http://"));
	}

	@Test
	void testPortEmpty() throws Exception {
		interceptor.setPort("");
		assertEquals(CONTINUE, interceptor.handleResponse(exc));
		assertFalse(matchSoap11(".*:80.*"));
		assertFalse(matchSoap12(".*:80.*"));
		assertFalse(matchHttp(".*:80.*"));
	}

	@Test
	void testPortDefault() throws Exception {
		assertEquals(CONTINUE, interceptor.handleResponse(exc));
		assertTrue(matchSoap11(".*:3011.*"));
		assertTrue(matchSoap12(".*:3011.*"));
		assertTrue(matchHttp(".*:3011.*"));
	}

	@Test
	void testPortSet() throws Exception {
		interceptor.setPort("2000");
		assertEquals(CONTINUE, interceptor.handleResponse(exc));
		assertTrue(matchSoap11(".*:2000.*"));
		assertTrue(matchSoap12(".*:2000.*"));
		assertTrue(matchHttp(".*:2000.*"));
	}

	@Test
	void testHostSet() throws Exception {
		interceptor.setHost("abc.com");
		assertEquals(CONTINUE, interceptor.handleResponse(exc));
		assertTrue(matchSoap11("http://abc.com.*"));
		assertTrue(matchSoap12("http://abc.com.*"));
		assertTrue(matchHttp("http://abc.com.*"));
	}

	@Test
	void testHostDefault() throws Exception {
		assertEquals(CONTINUE, interceptor.handleResponse(exc));
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

	private boolean matchSoap12(String pattern) throws Exception {
		return match(pattern, WSDL11_ADDRESS_SOAP12);
	}

	private boolean matchSoap11(String pattern) throws Exception {
		return match(pattern, WSDL11_ADDRESS_SOAP11);
	}

	private boolean matchHttp(String pattern) throws Exception {
		return match(pattern, WSDL11_ADDRESS_HTTP);
	}

	private boolean match(String pattern, QName addressElementName)
			throws Exception {
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
