/* Copyright 2005-2010 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.nio;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Test;

import com.predic8.membrane.core.http.Header;

/**
 * 
 */
public class MessageTest extends NioTestBase {

	@Test
	public void testRequestSingleBuffer() throws Exception {
		loadData("/get-request.msg");
		Message msg = new Message();
		ByteBuffer data = read(getDataLength());
		msg.receive(data);
		assertEquals(getDataLength(), msg.length());
		assertTrue(msg.isHeaderRead());
		assertNotNull(msg.getHeader());
		Header header = msg.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(-1, header.getContentLength());
		assertTrue(msg.isBodyComplete());
	}

	@Test
	public void testRequestMultipleBuffers() throws Exception {
		loadData("/post-request-large.msg");
		Message msg = new Message();
		ByteBuffer[] data = readMultiple(50, 200, 1024, 300, 2048);
		for (ByteBuffer b : data)
			msg.receive(b);
		assertEquals(getDataLength(), msg.length());
		assertTrue(msg.isHeaderRead());
		assertNotNull(msg.getHeader());
		Header header = msg.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(4674, header.getContentLength());
		assertEquals("application/xml", header.getContentType());
		assertTrue(msg.isBodyComplete());
	}

}
