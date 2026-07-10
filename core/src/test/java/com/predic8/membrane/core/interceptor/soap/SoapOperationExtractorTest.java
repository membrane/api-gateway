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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.interceptor.soap.SoapOperationExtractor.SOAP_OPERATION;
import static com.predic8.membrane.core.interceptor.soap.SoapOperationExtractor.SOAP_OPERATION_NS;
import static com.predic8.membrane.core.util.xml.parser.HardenedSaxParserTest.freePort;
import static com.predic8.membrane.core.util.xml.parser.HardenedSaxParserTest.startRecordingServer;
import static org.junit.jupiter.api.Assertions.*;

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

	@Test
	public void doesNotFetchExternalDtd() throws Exception {
		var received = new AtomicBoolean(false);
		int port = freePort();
		HttpRouter router = startRecordingServer(port, received);
		try {
			String maliciousSoap = """
					<?xml version='1.0'?>
					<!DOCTYPE s SYSTEM 'http://127.0.0.1:%d/x.dtd'>
					<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'>
					  <s:Body><op xmlns='http://example.com/'/></s:Body>
					</s:Envelope>
					""".formatted(port);
			Exchange exc = new Exchange(null);
			exc.setRequest(Request.post("http://test/").body(maliciousSoap).build());
			extractor.handleRequest(exc);
		} finally {
			router.shutdown();
		}
		assertFalse(received.get(), "SoapOperationExtractor must not fetch external DTD");
	}

	private Exchange getExchange(String path) throws IOException, URISyntaxException {
		Exchange exc = new Exchange(null);
		Request req = Request.post("http://test/")
				.body(getClass().getClassLoader().getResourceAsStream(path))
				.build();
		exc.setRequest(req);
		return exc;
	}

}
