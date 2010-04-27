package com.predic8.membrane.core.interceptor;

import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.TestUtil;
import com.predic8.membrane.core.ws.relocator.Relocator;


public class WSDLInterceptorTest extends TestCase {
	
	private HttpExchange exc;
	
	private WSDLInterceptor interceptor; 
	
	private byte[] bodyContent;
	
	@Override
	protected void setUp() throws Exception {
		bodyContent = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream("/blz-service.wsdl"));
		
		exc = new HttpExchange();
		exc.setRequest(TestUtil.getGetRequest());
		exc.setResponse(TestUtil.getOKResponse(bodyContent, "text/xml"));
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		exc.setRule(getRule());
		
		interceptor = new WSDLInterceptor();
	}
	
	@Test
	public void testProtocolSet() throws Exception {
		interceptor.setProtocol("https");
		assertEquals(interceptor.handleResponse(exc), Outcome.CONTINUE);
		
		assertTrue(getLocationAttributeFor(getElement(getParser(), Relocator.ADDRESS_SOAP11)).startsWith("https://"));
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
		return new ForwardingRule(new ForwardingRuleKey("localhost", ".*", ".*", 8080), "thomas-bayer.com", "80");
	}


	private XMLEventReader getParser() throws Exception {
		return XMLInputFactory.newInstance().createXMLEventReader(exc.getResponse().getBodyAsStream());
	}
	
	private String getLocationAttributeFor(StartElement element) {
		return element.getAttributeByName(new QName("" , "location")).getValue();
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
