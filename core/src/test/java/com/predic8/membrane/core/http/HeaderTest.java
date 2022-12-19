/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.activation.MimeType;
import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.*;

public class HeaderTest {

	private static Header header = new Header();

	@BeforeEach
	public void setUp() throws Exception {
		header.setContentType("text/xml; charset=utf-8");
		header.add("host", "127.0.0.1:2000");
		header.setAccept("application/soap+xml, application/dime, multipart/related, text/*");
		header.setAuthorization("alice", "secret");
	}

	@Test
	public void testGetHeader() {
		assertNotNull(header.getFirstValue("ACCEPT"));
		assertNotNull(header.getFirstValue("accept"));
		assertEquals("127.0.0.1:2000", header.getFirstValue("host"));
	}

	@Test
	public void testAuthorization() throws UnsupportedEncodingException {
		assertEquals("Basic YWxpY2U6c2VjcmV0",
				header.getFirstValue("Authorization"));
	}

	@Test
	public void testGetMimeType() throws Exception {
		assertTrue(new MimeType(header.getContentType()).match("text/xml"));
	}

	@Test
	public void testGetCharsetNull() throws Exception {
		Header header = new Header();
		header.setContentType("text/xml");
		assertEquals(Constants.UTF_8, header.getCharset());
	}

	@Test
	public void testStringCharset() throws Exception {
		Header header = new Header();
		header.setContentType("text/xml ;charset=\"UTF-8\"");
		assertEquals("UTF-8", header.getCharset());
	}

	@Test
	public void testGetCharsetCTNull() throws Exception {
		assertEquals(Constants.UTF_8, new Header().getCharset());
	}

	@Test
	public void testGetCharset() throws Exception {
		header.setContentType("text/xml; charset=utf-8");
		assertEquals("utf-8", header.getCharset());
	}

}
