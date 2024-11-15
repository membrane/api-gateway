/* Copyright 2013 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class MessageAnalyserTest {

	private static MessageAnalyser analyser;

	@BeforeAll
	public static void setUp() {
		analyser = new MessageAnalyser();
	}

	@Test
	public void extract() throws Exception {

		Exchange exc = getRequest("messageAnalyser/getBuecherSOAP11.xml");

		analyser.handleRequest(exc);

		assertEquals("Envelope", exc.getProperty(MessageAnalyser.REQUEST_ROOT_ELEMENT_NAME));
		assertEquals(Constants.SOAP11_NS, exc.getProperty(MessageAnalyser.REQUEST_ROOT_ELEMENT_NS));
		assertEquals(Constants.SoapVersion.SOAP11, exc.getProperty(MessageAnalyser.REQUEST_SOAP_VERSION));
		assertEquals("getBuecher", exc.getProperty(MessageAnalyser.REQUEST_SOAP_OPERATION));
		assertEquals("http://predic8.de", exc.getProperty(MessageAnalyser.REQUEST_SOAP_OPERATION_NS));

	}

	@Test
	public void extractFromResponse() throws Exception {

		Exchange exc = getResponse("messageAnalyser/getBuecherResponseSOAP11.xml");

		analyser.handleResponse(exc);

		assertEquals("Envelope", exc.getProperty(MessageAnalyser.RESPONSE_ROOT_ELEMENT_NAME));
		assertEquals(Constants.SOAP11_NS, exc.getProperty(MessageAnalyser.RESPONSE_ROOT_ELEMENT_NS));
		assertEquals(Constants.SoapVersion.SOAP11, exc.getProperty(MessageAnalyser.RESPONSE_SOAP_VERSION));
		assertEquals("getBuecher", exc.getProperty(MessageAnalyser.RESPONSE_SOAP_OPERATION));
		assertEquals("http://predic8.de", exc.getProperty(MessageAnalyser.RESPONSE_SOAP_OPERATION_NS));
	}

	private Exchange getResponse(String path) throws IOException {
		Exchange exc = new Exchange(null);
		exc.setResponse(Response.ok().body(getClass().getClassLoader().getResourceAsStream(path), true).build());
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
