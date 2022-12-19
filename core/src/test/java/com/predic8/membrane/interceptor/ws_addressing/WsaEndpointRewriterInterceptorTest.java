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

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.ws_addressing.WsaEndpointRewriterInterceptor;
import com.predic8.membrane.core.util.MessageUtil;

@Disabled
public class WsaEndpointRewriterInterceptorTest {
	private WsaEndpointRewriterInterceptor rewriter;
	private Exchange exc;

	@BeforeEach
	public void setUp() {
		rewriter = new WsaEndpointRewriterInterceptor();
		exc = new Exchange(null);
	}

	@Test
	public void testRewriterInterceptor() throws Exception {
		exc.setRequest(MessageUtil.getPostRequest("http://localhost:9000/SoapContext/SoapPort?wsdl"));
		InputStream input = WsaEndpointRewriterTest.class.getResourceAsStream("/interceptor/ws_addressing/body.xml");
		exc.getRequest().setBody(new Body(input));

		assertEquals(Outcome.CONTINUE, rewriter.handleRequest(exc));
		assertEquals(exc.getProperty("messageId"), "urn:uuid:62a0de08-055a-4da7-aefa-730af9dbc6b6");
	}
}