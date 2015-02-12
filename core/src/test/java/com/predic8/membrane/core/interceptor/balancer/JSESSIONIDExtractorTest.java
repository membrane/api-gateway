/* Copyright 2011 predic8 GmbH, www.predic8.com

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


import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.http.*;

public class JSESSIONIDExtractorTest extends TestCase {
	

	@Test
	public void testRequestExtraction() throws Exception {
		Request req = new Request();	

		JSESSIONIDExtractor extractor = new JSESSIONIDExtractor();
				
		req.setHeader(getHeader("path=root/dir ; JSESSIONID=555555"));
		assertEquals("555555", extractor.getSessionId(req));
		
		req.setHeader(getHeader("path=root/dir ; JSESSIONID=555555; name=jim"));
		assertEquals("555555", extractor.getSessionId(req));

		req.setHeader(getHeader("JSESSIONID=555555  ;path=root/dir;"));
		assertEquals("555555", extractor.getSessionId(req));

		req.setHeader(getHeader("name=jim;path=root/dir;"));
		assertEquals(false, extractor.hasSessionId(req));

		req.setHeader(getHeader(null));
		assertEquals(false, extractor.hasSessionId(req));
	}

	@Test
	public void testResponseExtraction() throws Exception {
		Response res = new Response();	

		JSESSIONIDExtractor extractor = new JSESSIONIDExtractor();
				
		res.setHeader(getHeader("path=root/dir ; JSESSIONID=555555"));
		assertEquals("555555", extractor.getSessionId(res));
		
		res.setHeader(getHeader("path=root/dir ; JSESSIONID=555555; name=jim"));
		assertEquals("555555", extractor.getSessionId(res));

		res.setHeader(getHeader("JSESSIONID=555555  ;path=root/dir;"));
		assertEquals("555555", extractor.getSessionId(res));

		res.setHeader(getHeader("name=jim;path=root/dir;"));
		assertEquals(false, extractor.hasSessionId(res));

		res.setHeader(getHeader(null));
		assertEquals(false, extractor.hasSessionId(res));
	}

	private Header getHeader(String cookie) {
		Header h = new Header();
		h.setContentType("application/soap+xml");
		if (cookie != null ) h.setValue("Cookie", cookie);
		return h;
	}

}
