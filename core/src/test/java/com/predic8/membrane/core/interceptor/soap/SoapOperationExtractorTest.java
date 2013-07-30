package com.predic8.membrane.core.interceptor.soap;

import java.io.IOException;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;

public class SoapOperationExtractorTest {

	private SoapOperationExtractor extractor; 
	
	@Before
	public void setUp() {
		extractor = new SoapOperationExtractor();
	}
	
	@Test
	public void extract() throws Exception {
		
		Exchange exc = getExchange("soapOperationExtractor/getBuecher.xml");
		
		extractor.handleRequest(exc);
		
		Assert.assertEquals("getBuecher", exc.getProperty(SoapOperationExtractor.SOAP_OPERATION));
		Assert.assertEquals("http://predic8.de", exc.getProperty(SoapOperationExtractor.SOAP_OPERATION_NS));
		
	}

	@Test
	public void notSoap() throws Exception {
		
		Exchange exc = getExchange("soapOperationExtractor/notSoap.xml");
		
		extractor.handleRequest(exc);
		
		Assert.assertNull(exc.getProperty(SoapOperationExtractor.SOAP_OPERATION));
		Assert.assertNull(exc.getProperty(SoapOperationExtractor.SOAP_OPERATION_NS));
		
	}

	@Test
	public void nonEmptyHeader() throws Exception {
		
		Exchange exc = getExchange("soapOperationExtractor/getBuecherWithHeader.xml");
		
		extractor.handleRequest(exc);
		
		Assert.assertEquals("getBuecher", exc.getProperty(SoapOperationExtractor.SOAP_OPERATION));
		Assert.assertEquals("http://predic8.de", exc.getProperty(SoapOperationExtractor.SOAP_OPERATION_NS));
		
	}

	private Exchange getExchange(String path) throws IOException {
		Exchange exc = new Exchange(null);
		Request req = new Request();
		req.create("POST", "http://test", "HTTP/", new Header(), getClass().getClassLoader().getResourceAsStream(path));
		exc.setRequest(req);
		return exc;
	}

}
