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
package com.predic8.membrane.core.interceptor.soap.ws_addressing;

import com.predic8.membrane.core.exchange.*;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.*;

import javax.xml.stream.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

public class WsaEndpointRewriterTest {
	private InputStream input;

	@BeforeEach
	public void setUp() {
		input = WsaEndpointRewriterTest.class.getResourceAsStream("/ws/ws-addessing.xml");
	}

	@Test
	public void testRewriteEndpointAddress() throws XMLStreamException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		WsaEndpointRewriter rewriter = new WsaEndpointRewriter(new DecoupledEndpointRegistry());

		int port = 2000;
		rewriter.rewriteEndpoint(input, output, port, new Exchange(null));

		String rewrittenXml = output.toString();

		System.out.println("Rewritten" + rewrittenXml);

		assertTrue(rewrittenXml.contains("http://localhost:" + port + "/decoupled_endpoint"));
	}
}
