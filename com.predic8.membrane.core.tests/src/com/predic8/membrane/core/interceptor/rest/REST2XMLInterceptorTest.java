package com.predic8.membrane.core.interceptor.rest;

import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import junit.framework.TestCase;

import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.ws.relocator.Relocator;


public class REST2XMLInterceptorTest extends TestCase {
	
	private HttpExchange exc;
	
	private HTTP2XMLInterceptor interceptor = new HTTP2XMLInterceptor(); 
	
	private byte[] bodyContent;
	XPath xpath = XPathFactory.newInstance().newXPath();
	
	@Override
	protected void setUp() throws Exception {
		bodyContent = ByteUtil.getByteArrayData(this.getClass().getResourceAsStream("/blz-service.wsdl"));
		
		exc = new HttpExchange();
		exc.setRequest(MessageUtil.getGetRequest("http://localhost/axis2/services/BLZService?wsdl"));
		exc.setResponse(MessageUtil.getOKResponse(bodyContent, "text/xml"));
		exc.setOriginalHostHeader("thomas-bayer.com:80");
		
	}
	
	@Test
	public void testMethode() throws Exception {
		interceptor.handleRequest(exc);
		
		//assertXPath("/request/metadata/method", "GET");
	}

	private void assertXPath(String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc.getRequest().getBodyAsStream())));
	}
	
}
