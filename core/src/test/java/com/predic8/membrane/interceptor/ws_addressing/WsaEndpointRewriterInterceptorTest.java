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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.ws_addressing.*;
import org.junit.jupiter.api.*;

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
				    <wsa:MessageID>
				      uuid:a8addabf-095f-493e-b59e-325f5b0a599c
				    </wsa:MessageID>
				    <wsa:ReplyTo>
				      <wsa:Address>https://api.predic8.de/client</wsa:Address>
				    </wsa:ReplyTo>
				    <wsa:To>https://api.predic8.de/shop/v2</wsa:To>
				    <wsa:Action>https://api.predic8.de/shop/v2/getproduct</wsa:Action>
				   </S:Header>
				   <S:Body>
				     <foo/>
				   </S:Body>
				 </S:Envelope>
				""";
		Exchange exc = Request.post("/foo").body(body).buildExchange();
		rewriter.setHost("membrane-api.io");
		assertEquals(CONTINUE, rewriter.handleRequest(exc));
		assertTrue(exc.getRequest().getBodyAsStringDecoded().contains("membrane-api.io"));
	}
}