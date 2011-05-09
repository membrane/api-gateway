package com.predic8.membrane.core.interceptor.rest;

import static junit.framework.Assert.assertEquals;

import java.io.StringReader;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import junit.framework.TestCase;

import org.junit.Test;
import org.xml.sax.InputSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.xml.Param;
import com.predic8.membrane.core.http.xml.Path;
import com.predic8.membrane.core.http.xml.Query;
import com.predic8.membrane.core.http.xml.Request;
import com.predic8.membrane.core.http.xml.URI;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.MessageUtil;
import com.predic8.membrane.core.ws.relocator.Relocator;


public class HTTP2XMLInterceptorTest extends TestCase {
	
	private Exchange exc;
	
	private HTTP2XMLInterceptor interceptor = new HTTP2XMLInterceptor(); 
	
	XPath xpath = XPathFactory.newInstance().newXPath();
	
	@Override
	protected void setUp() throws Exception {
		exc = new Exchange();
		exc.setRequest(MessageUtil.getGetRequest("http://localhost/axis2/services/BLZService?wsdl"));
		exc.getRequest().setUri("http://localhost:8080/manager/person?vorname=jim&nachname=panse");
		exc.getRequest().setMethod("POST");
		exc.getRequest().setVersion("1.1");
		exc.getRequest().getHeader().add("Host", "localhost:8080");
	}
	
	@Test
	public void testRequest() throws Exception {

		interceptor.handleRequest(exc);
		
		assertXPath("/request/@method", "POST");
		assertXPath("/request/@http-version", "1.1");
		assertXPath("/request/uri/@value", "http://localhost:8080/manager/person?vorname=jim&nachname=panse");
		assertXPath("/request/uri/path/component[1]", "manager");
		assertXPath("/request/uri/path/component[2]", "person");
		assertXPath("/request/uri/query/param[1]/@name", "vorname");
		assertXPath("/request/uri/query/param[1]", "jim");
		assertXPath("/request/uri/query/param[2]/@name", "nachname");
		assertXPath("/request/uri/query/param[2]", "panse");
		assertXPath("/request/headers/header/@name", "Host");
		assertXPath("/request/headers/header", "localhost:8080");		
	}

	@Test
	public void parseXML() throws Exception {
		String xml = "<request method='POST' http-version='1.1'><uri value='http://localhost:8080/manager/person?vorname=jim&amp;nachname=panse'><host>localhost</host><port>8080</port><path><component>manager</component><component>person</component></path><query><param name='vorname'>jim</param><param name='nachname'>panse</param></query></uri></request>";
		
		XMLStreamReader r = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
		r.next(); //skip DocumentNode
		Request req = new Request();
		req.parse(r);

		assertEquals("POST", req.getMethod());
		assertEquals("1.1", req.getHttpVersion());		
		assertURI(req.getUri());
	}

	private void assertURI(URI uri) {
		assertEquals("http://localhost:8080/manager/person?vorname=jim&nachname=panse", uri.getValue());
		assertEquals("localhost", uri.getHost());
		assertEquals(8080, uri.getPort());	
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

	private void assertXPath(String xml, String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(new StringReader(xml))));
	}
	
	private void assertXPath(String xpathExpr, String expected) throws XPathExpressionException {
		assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc.getRequest().getBodyAsStream())));
	}
	
	
}
