/* Copyright 2011, 2025 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSESSIONIDExtractorTest extends AbstractSessionIdExtractorTest {


	@Test
	public void testRequestExtraction() throws Exception {
		Request req = new Request();

		JSESSIONIDExtractor extractor = new JSESSIONIDExtractor();

		req.setHeader(getHeader("path=root/dir ; JSESSIONID=555555"));
		assertEquals("555555", extractor.getSessionId(getExchange(req), REQUEST));

		req.setHeader(getHeader("path=root/dir ; JSESSIONID=555555; name=jim"));
		assertEquals("555555", extractor.getSessionId(getExchange(req), REQUEST));

		req.setHeader(getHeader("JSESSIONID=555555  ;path=root/dir;"));
		assertEquals("555555", extractor.getSessionId(getExchange(req), REQUEST));

		req.setHeader(getHeader("name=jim;path=root/dir;"));
		assertEquals(false, extractor.hasSessionId(getExchange(req), REQUEST));

		req.setHeader(getHeader(null));
		assertEquals(false, extractor.hasSessionId(getExchange(req), REQUEST));
	}

    @Test
	public void testResponseExtraction() throws Exception {
		Response res = new Response();

		JSESSIONIDExtractor extractor = new JSESSIONIDExtractor();

		res.setHeader(getHeader("path=root/dir ; JSESSIONID=555555"));
		assertEquals("555555", extractor.getSessionId(getExchange(res), RESPONSE));

		res.setHeader(getHeader("path=root/dir ; JSESSIONID=555555; name=jim"));
		assertEquals("555555", extractor.getSessionId(getExchange(res), RESPONSE));

		res.setHeader(getHeader("JSESSIONID=555555  ;path=root/dir;"));
		assertEquals("555555", extractor.getSessionId(getExchange(res), RESPONSE));

		res.setHeader(getHeader("name=jim;path=root/dir;"));
		assertEquals(false, extractor.hasSessionId(getExchange(res), RESPONSE));

		res.setHeader(getHeader(null));
		assertEquals(false, extractor.hasSessionId(getExchange(res), RESPONSE));
	}

	private Header getHeader(String cookie) {
		Header h = new Header();
		h.setContentType("application/soap+xml");
		if (cookie != null ) h.setValue("Cookie", cookie);
		return h;
	}
}
