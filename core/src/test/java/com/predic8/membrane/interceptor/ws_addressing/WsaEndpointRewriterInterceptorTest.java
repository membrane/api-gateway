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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ws_addressing.WsaEndpointRewriterInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class WsaEndpointRewriterInterceptorTest {
	private WsaEndpointRewriterInterceptor rewriter;
	private Exchange exc;

	@BeforeEach
	public void setUp() throws Exception {
		Router router = new HttpRouter();
		router.init();
		rewriter = new WsaEndpointRewriterInterceptor();
		rewriter.init(router);
		exc = new Exchange(null);
	}

	@Test
	public void testRewriterInterceptor() throws Exception {
		exc.setRequest(new Request.Builder().post("http://localhost:9000/SoapContext/SoapPort?wsdl").build());
			exc.getRequest().setBody(new Body("""
					<S:Envelope xmlns:S="http://www.w3.org/2003/05/soap-envelope"     \s
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
					""".getBytes()));

		assertEquals(Outcome.CONTINUE, rewriter.handleRequest(exc));
		assertEquals("uuid:a8addabf-095f-493e-b59e-325f5b0a599c",  ((String)exc.getProperty("messageId")).trim());
	}
}