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
package com.predic8.membrane.core.interceptor.soap;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;

import static com.predic8.membrane.core.interceptor.soap.SoapOperationExtractor.SOAP_OPERATION;
import static com.predic8.membrane.core.interceptor.soap.SoapOperationExtractor.SOAP_OPERATION_NS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SoapOperationExtractorTest {

	private static SoapOperationExtractor extractor;

	@BeforeAll
	public static void setUp() {
		extractor = new SoapOperationExtractor();
	}

	@Test
	public void extract() throws Exception {

		Exchange exc = getExchange("soapOperationExtractor/getBuecher.xml");

		extractor.handleRequest(exc);

		assertEquals("getBuecher", exc.getProperty(SOAP_OPERATION));
		assertEquals("http://predic8.de", exc.getProperty(SOAP_OPERATION_NS));

	}

	@Test
	public void notSoap() throws Exception {

		Exchange exc = getExchange("soapOperationExtractor/notSoap.xml");

		extractor.handleRequest(exc);

		assertNull(exc.getProperty(SOAP_OPERATION));
		assertNull(exc.getProperty(SOAP_OPERATION_NS));

	}

	@Test
	public void nonEmptyHeader() throws Exception {

		Exchange exc = getExchange("soapOperationExtractor/getBuecherWithHeader.xml");

		extractor.handleRequest(exc);

		assertEquals("getBuecher", exc.getProperty(SOAP_OPERATION));
		assertEquals("http://predic8.de", exc.getProperty(SOAP_OPERATION_NS));

	}

	private Exchange getExchange(String path) throws IOException {
		Exchange exc = new Exchange(null);
		Request req = new Request();
		req.create("POST", "http://test", "HTTP/", new Header(), getClass().getClassLoader().getResourceAsStream(path));
		exc.setRequest(req);
		return exc;
	}

}
