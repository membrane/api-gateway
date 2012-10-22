/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

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


import static com.predic8.membrane.core.util.ByteUtil.getByteArrayData;

import java.io.IOException;

import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.http.*;

public class XMLSessionIdExtractorTest extends TestCase {
	

	@Test
	public void testSessionIdExtraction() throws Exception {
		Request res = new Request();	
		res.setHeader(getHeader());
		res.setBodyContent(getBodyContent());

		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("session");
		extractor.setNamespace("http://predic8.com/session/");
				
		assertEquals("555555", extractor.getSessionId(res));
		
	}

	@Test
	public void testSessionIdExtractionNoNS() throws Exception {
		Response res = new Response();		
		res.setHeader(getHeader());
		res.setBodyContent(getBodyContent());

		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("ses:session");
		
		assertEquals("555555", extractor.getSessionId(res));
		
	}
	
	@Test
	public void testPerformace() throws Exception {
		Response res = new Response();		
		res.setHeader(getHeader());
		res.setBodyContent(getBodyContent());

		XMLElementSessionIdExtractor extractor = new XMLElementSessionIdExtractor();
		extractor.setLocalName("session");
		extractor.setNamespace("http://predic8.com/session/");
		
		//long t = System.currentTimeMillis();
		for (int i = 0; i < 1000; i++) {
			extractor.getSessionId(res);
		}		
		//System.out.println("Time (ms): "+(System.currentTimeMillis()-t));
	}

	private byte[] getBodyContent() throws IOException {
		return getByteArrayData(getClass().getResourceAsStream("/getBankwithSession555555.xml"));
	}
	
	private Header getHeader() {
		Header h = new Header();
		h.setContentType("application/soap+xml");
		return h;
	}

}
