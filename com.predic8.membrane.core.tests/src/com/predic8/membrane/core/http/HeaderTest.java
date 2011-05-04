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

package com.predic8.membrane.core.http;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import javax.activation.MimeType;

import org.junit.Before;
import org.junit.Test;
public class HeaderTest {

	private static Header header = new Header();
	
	@Before
	public void setUp() throws Exception {
		header.add("Content-Type","text/xml; charset=utf-8");
		header.add("host","127.0.0.1:2000");
		header.add("ACCEPT","application/soap+xml, application/dime, multipart/related, text/*");
	}

	@Test	
	public void testGetHeader() {
		assertNotNull(header.getFirstValue("ACCEPT"));
		assertNotNull(header.getFirstValue("accept"));
		assertEquals("127.0.0.1:2000",header.getFirstValue("host"));
	}
	
	@Test
	public void testGetMimeType() throws Exception {
		assertTrue(new MimeType(header.getContentType()).match("text/xml"));
	}

	@Test
	public void testGetCharsetNull() throws Exception {
		Header header = new Header();
		header.add("Content-Type", "text/xml");
		assertNull(header.getCharset());
	}
	
	@Test
	public void testGetCharsetCTNull() throws Exception {
		assertNull(new Header().getCharset());
	}
	
	@Test
	public void testGetCharset() throws Exception {
		assertEquals("utf-8", header.getCharset());
	}
	
}
