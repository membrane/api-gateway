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

import jakarta.activation.MimeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static org.junit.jupiter.api.Assertions.*;

public class HeaderTest {

	private static final Header header = new Header();

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
	public void testAuthorization() {
		assertEquals("Basic YWxpY2U6c2VjcmV0",
				header.getFirstValue("Authorization"));
	}

	@Test
	public void testGetMimeType() throws Exception {
		assertTrue(new MimeType(header.getContentType()).match(TEXT_XML));
	}

	@Test
	public void testGetCharsetNull() {
		Header header = new Header();
		header.setContentType(TEXT_XML);
		assertEquals(UTF_8, header.getCharset());
	}

	@Test
	public void testStringCharset() {
		Header header = new Header();
		header.setContentType("text/xml ;charset=\"UTF-8\"");
		assertEquals(UTF_8, header.getCharset());
	}

	@Test
	public void testGetCharsetCTNull() {
		assertEquals(UTF_8, new Header().getCharset());
	}

	@Test
	public void testGetCharset() {
		header.setContentType("text/xml; charset=utf-8");
		assertEquals("utf-8", header.getCharset());
	}

	@ParameterizedTest
	@ValueSource(strings = {"zip","octet-stream"})
	void isBinaryContentTypeSubtypes(String subtype) {
		assertTrue(isBinary("foo/" + subtype),subtype);
	}

	@ParameterizedTest
	@ValueSource(strings = {"audio","image","video"})
	void isBinaryContentTypePrimaryTypes(String primary) {
		assertTrue(isBinary(primary + "/foo"),primary);
	}
}
