/* Copyright 2011, 2012, 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;


import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static org.junit.jupiter.api.Assertions.*;

class XMLSessionIdExtractorTest extends AbstractSessionIdExtractorTest {


	@Test
    void sessionIdExtraction() throws Exception {
		Request req = new Request();
		req.setHeader(getHeader());
		req.setBodyContent(getBodyContent());

		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("session");
		extractor.setNamespace("http://predic8.com/session/");

		assertEquals("555555", extractor.getSessionId(getExchange(req), REQUEST));

	}

	@Test
	void sessionId_extractionNoNS() throws Exception {
		Response res = new Response();
		res.setHeader(getHeader());
        res.setBodyContent(getBodyContent());

		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("session");


		assertEquals("555555", extractor.getSessionId(getExchange(res), RESPONSE));
	}

	private byte[] getBodyContent() throws IOException {
		return getClass().getResourceAsStream("/getBankwithSession555555.xml").readAllBytes();
	}

	private Header getHeader() {
		Header h = new Header();
		h.setContentType("application/soap+xml");
		return h;
	}
}
