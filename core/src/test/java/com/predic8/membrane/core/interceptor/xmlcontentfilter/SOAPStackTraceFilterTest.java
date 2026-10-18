/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.test.StringAssertions.assertContains;
import static com.predic8.membrane.test.StringAssertions.assertContainsNot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SOAPStackTraceFilterTest {

	private static SOAPStackTraceFilterInterceptor interceptor() throws Exception {
		SOAPStackTraceFilterInterceptor interceptor = new SOAPStackTraceFilterInterceptor();
		interceptor.init(new DummyTestRouter());
		return interceptor;
	}

	private Request getRequest() throws IOException {
		Request r = new Request();
		r.setBody(new Body(getClass().getResourceAsStream("/soap-sample/soapfault-with-java-stacktrace.xml")));
		return r;
	}

	@Test
	void doit() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(getRequest());
		interceptor().handleRequest(exc);
		String body = exc.getRequest().getBody().toString();
		assertContainsNot("MyService", body);
		assertContains("soap:Server", body);
		assertContainsNot("stacktrace", body);
	}

	private static String faultWithDoctype() {
		return """
				<!DOCTYPE soap:Envelope>
				<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
				    <soap:Body>
				        <soap:Fault>
				            <faultcode>soap:Server</faultcode>
				            <faultstring>Internal Server Error</faultstring>
				            <detail>
				                <stacktrace>at com.example.service.MyService.process(MyService.java:45)</stacktrace>
				            </detail>
				        </soap:Fault>
				    </soap:Body>
				</soap:Envelope>
				""";
	}

	@Test
	void doctypeInResponseIsRejected() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().post("/foo").contentType("text/xml").body("<a/>").build());
		exc.setResponse(Response.internalServerError().contentType("text/xml").body(faultWithDoctype()).build());
		assertEquals(ABORT, interceptor().handleResponse(exc));
		assertEquals(500, exc.getResponse().getStatusCode());
		assertEquals("application/problem+xml", exc.getResponse().getHeader().getContentType());
		assertContainsNot("MyService", exc.getResponse().getBodyAsStringDecoded());
	}

	@Test
	void doctypeInRequestIsRejected() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().post("/foo").contentType("text/xml").body(faultWithDoctype()).build());
		assertEquals(ABORT, interceptor().handleRequest(exc));
		assertEquals(400, exc.getResponse().getStatusCode());
		assertEquals("application/problem+xml", exc.getResponse().getHeader().getContentType());
	}

	@Test
	void unreadableBodyIsForwarded() throws Exception {
		Exchange exc = new Exchange(null);
		exc.setRequest(new Request.Builder().post("/foo").contentType("text/xml").header("Content-Length", "10").body(new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("connection reset");
			}
		}).build());
		final var interceptor = interceptor();
		assertThrows(ReadingBodyException.class, () -> interceptor.handleRequest(exc));
		assertNull(exc.getResponse());
	}

}
