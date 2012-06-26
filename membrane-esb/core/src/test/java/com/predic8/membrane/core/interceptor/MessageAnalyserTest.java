package com.predic8.membrane.core.interceptor;

import java.io.IOException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

public class MessageAnalyserTest {

	private MessageAnalyser analyser; 
	
	@Before
	public void setUp() {
		analyser = new MessageAnalyser();
	}
	
	@Test
	public void extract() throws Exception {
		
		Exchange exc = getRequest("messageAnalyser/getBuecherSOAP11.xml");
		
		analyser.handleRequest(exc);
		
		Assert.assertEquals("Envelope", exc.getProperty(MessageAnalyser.REQUEST_ROOT_ELEMENT_NAME));
		Assert.assertEquals(Constants.SOAP11_NS, exc.getProperty(MessageAnalyser.REQUEST_ROOT_ELEMENT_NS));
		Assert.assertEquals(Constants.SOAP11_VERION, exc.getProperty(MessageAnalyser.REQUEST_SOAP_VERSION));
		Assert.assertEquals("getBuecher", exc.getProperty(MessageAnalyser.REQUEST_SOAP_OPERATION));
		Assert.assertEquals("http://predic8.de", exc.getProperty(MessageAnalyser.REQUEST_SOAP_OPERATION_NS));
		
	}

	@Test
	public void extractFromResponse() throws Exception {
		
		Exchange exc = getResponse("messageAnalyser/getBuecherResponseSOAP11.xml");
		
		analyser.handleResponse(exc);
		
		Assert.assertEquals("Envelope", exc.getProperty(MessageAnalyser.RESPONSE_ROOT_ELEMENT_NAME));
		Assert.assertEquals(Constants.SOAP11_NS, exc.getProperty(MessageAnalyser.RESPONSE_ROOT_ELEMENT_NS));
		Assert.assertEquals(Constants.SOAP11_VERION, exc.getProperty(MessageAnalyser.RESPONSE_SOAP_VERSION));
		Assert.assertEquals("getBuecher", exc.getProperty(MessageAnalyser.RESPONSE_SOAP_OPERATION));
		Assert.assertEquals("http://predic8.de", exc.getProperty(MessageAnalyser.RESPONSE_SOAP_OPERATION_NS));
		
	}

	private Exchange getResponse(String path) throws IOException {
		Exchange exc = new Exchange(null);
		exc.setResponse(Response.ok().body(getClass().getClassLoader().getResourceAsStream(path)).build());
		return exc;
	}

	private Exchange getRequest(String path) throws IOException {
		Exchange exc = new Exchange(null);
		Request req = new Request();
		req.create("POST", "http://test", "HTTP/", new Header(), getClass().getClassLoader().getResourceAsStream(path));
		exc.setRequest(req);
		return exc;
	}

}
