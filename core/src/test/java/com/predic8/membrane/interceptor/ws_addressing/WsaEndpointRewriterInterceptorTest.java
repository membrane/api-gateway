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
package com.predic8.membrane.interceptor.ws_addressing;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.ws_addressing.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;


public class WsaEndpointRewriterInterceptorTest {
	private WsaEndpointRewriterInterceptor rewriter;

	@BeforeEach
	public void setUp() throws Exception {
		Router router = new HttpRouter();
		router.init();
		rewriter = new WsaEndpointRewriterInterceptor();
		rewriter.init(router);
	}

	@Test
	void rewriterInterceptor() throws Exception {

		String body = """
				<S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope"
				                xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
				   <S:Header>
				    <wsa:ReplyTo>
				      <wsa:Address>https://api.predic8.de/client</wsa:Address>
				    </wsa:ReplyTo>
				   </S:Header>
				 </S:Envelope>
				""";

		Exchange exchange = ok(body).contentType(APPLICATION_SOAP_XML).buildExchange();
		rewriter.setProtocol("https");
		rewriter.setHost("membrane-api.io");
		rewriter.setPort(8080);
		assertEquals(CONTINUE, rewriter.handleResponse(exchange));

		String rewritten = exchange.getResponse().getBodyAsStringDecoded();

		System.out.println(rewritten);
		assertTrue(rewritten.contains("https://membrane-api.io:8080/client"));
		assertFalse(rewritten.contains("api.predic8.de"));
	}
}