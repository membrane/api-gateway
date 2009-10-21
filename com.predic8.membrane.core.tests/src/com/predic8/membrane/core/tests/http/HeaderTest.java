/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.tests.http;
import javax.activation.MimeType;

import junit.framework.TestCase;

import com.predic8.membrane.core.http.Header;


public class HeaderTest extends TestCase {

	private static Header header = new Header();
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		header.add("Content-Type","text/xml; charset=utf-8");
		header.add("host","127.0.0.1:2000");
		header.add("ACCEPT","application/soap+xml, application/dime, multipart/related, text/*");
	}


	public void testGetHeader() {
		assertNotNull(header.getFirstValue("ACCEPT"));
		assertNotNull(header.getFirstValue("accept"));
		assertEquals("127.0.0.1:2000",header.getFirstValue("host"));
	}
	
	public void testGetMimeType() throws Exception {
		assertTrue(new MimeType(header.getContentType()).match("text/xml"));
	}
	
	public void testConstructor() throws Exception {
		Header h1 = new Header(header);
		assertEquals("127.0.0.1:2000", h1.getHost());
	}

}
