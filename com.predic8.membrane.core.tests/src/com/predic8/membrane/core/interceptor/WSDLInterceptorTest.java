package com.predic8.membrane.core.interceptor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.ws.relocator.Relocator;
public class WSDLInterceptorTest {
	
	private Exchange exc;
	
	private WSDLInterceptor interceptor; 
	
	@Before
	public void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("/axis2/services/BLZService?wsdl"));
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/blz-service.wsdl");
		Response okResponse = MessageUtil.getOKResponse(ByteUtil.getByteArrayData(resourceAsStream), "text/xml; charset=utf-8");
		exc.setResponse(okResponse);
		
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		exc.setRule(getRule());
		
		interceptor = new WSDLInterceptor();
	}
	
	@Test
	public void testProtocolSet() throws Exception {
		interceptor.setProtocol("https");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		
		XMLEventReader parser = getParser();
		
		System.out.println("parser is: " + parser);
		
		StartElement element = getElement(parser, Relocator.ADDRESS_SOAP11);
		String locationAttr = getLocationAttributeFor(element);
		System.out.println("location attribute is: " + locationAttr);
		assertTrue(locationAttr.startsWith("https://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_SOAP12)).startsWith("https://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_HTTP)).startsWith("https://"));
	}
	
	@Test
	public void testProtocolDefault() throws Exception {
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_SOAP11)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_SOAP12)).startsWith("http://"));
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_HTTP)).startsWith("http://"));
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
		assertTrue(matchSoap11(".*:8080.*"));
		assertTrue(matchSoap12(".*:8080.*"));
		assertTrue(matchHttp(".*:8080.*"));
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
	
	private Rule getRule() {		
		return new ForwardingRule(new ForwardingRuleKey("localhost", ".*", ".*", 8080), "thomas-bayer.com", 80);
	}

	private XMLEventReader getParser() throws Exception {
		return XMLInputFactory.newInstance().createXMLEventReader(new InputStreamReader(exc.getResponse().getBodyAsStream(), exc.getResponse().getCharset()));
	}
	
	private String getLocationAttributeFor(StartElement element) {
		return element.getAttributeByName(new QName(Constants.NS_UNDEFINED , "location")).getValue();
	}
	
	private boolean matchSoap12(String pattern) throws XMLStreamException, Exception {
		return match(pattern, Relocator.ADDRESS_SOAP12);
	}

	private boolean matchSoap11(String pattern) throws XMLStreamException, Exception {
		return match(pattern, Relocator.ADDRESS_SOAP11);
	}
	
	private boolean matchHttp(String pattern) throws XMLStreamException, Exception {
		return match(pattern, Relocator.ADDRESS_HTTP);
	}
	
	private boolean match(String pattern, QName addressElementName) throws XMLStreamException, Exception {
		return Pattern.compile(pattern).matcher(getLocationAttributeFor(getElement(getParser(), addressElementName))).matches();
	}
	
	private StartElement getElement(XMLEventReader parser, QName qName) throws XMLStreamException {
		while (parser.hasNext()) {
			XMLEvent event = parser.nextEvent();
			
			if ( event.isStartElement() ) {
				if ( event.asStartElement().getName().equals(qName)) {
					return event.asStartElement();
				} 
			}
		}
		throw new RuntimeException("element " + qName + " not found in response");
	}
	
}
