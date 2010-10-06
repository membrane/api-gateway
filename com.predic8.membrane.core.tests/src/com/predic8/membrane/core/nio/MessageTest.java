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
import java.nio.channels.Channels;

import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Header;

/**
 * 
 */
public class MessageTest extends NioTestBase {

	@Test
	public void testGetRequestSingleBuffer() throws Exception {
		loadData("/get-request.msg");
		Request req = new Request();
		ByteBuffer data = read(getDataLength());
		req.receive(data);
		assertEquals(getDataLength(), req.length());
		assertTrue(req.isHeaderRead());
		assertNotNull(req.getHeader());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(-1, header.getContentLength());
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testPostRequestMultipleBuffers() throws Exception {
		loadData("/post-request-large.msg");
		Request req = new Request();
		ByteBuffer[] data = readMultiple(50, 200, 1024, 300, 2048);
		for (int i = 0; i < data.length - 1; i++)
			req.receive(data[i]);
		assertTrue(req.isHeaderRead());
		assertNotNull(req.getHeader());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(4674, header.getContentLength());
		assertEquals("application/xml", header.getContentType());
		assertFalse(req.isBodyComplete());
		req.receive(data[data.length - 1]);
		assertEquals(getDataLength(), req.length());
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testPostRequestHttp10() throws Exception {
		loadData("/post-request-http10.msg");
		Request req = new Request();
		ByteBuffer[] data = readMultiple(50, 200, 1024, 300, 2048);
		for (int i = 0; i < data.length - 1; i++)
			req.receive(data[i]);
		assertTrue(req.isHeaderRead());
		assertTrue(req.isHttp10());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(-1, header.getContentLength());
		assertFalse(req.isBodyComplete());
		req.endOfStream();
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testPostRequestChunked() throws Exception {
		loadData("/post-request-chunked.msg");
		Request req = new Request();
		ByteBuffer[] data = readMultiple(422, 206, 1031, 307, 2055);
		req.receive(data[0]);
		assertTrue(req.isHeaderRead());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertTrue(header.isChunked());
		assertEquals(-1, header.getContentLength());
		assertFalse(req.isBodyComplete());
		for (int i = 1; i < data.length; i++) {
			req.receive(data[i]);
		}
		req.receive(newChunk(ByteBuffer.allocate(0)));
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testEmptyBodyDecoder() throws Exception {
		loadData("/get-request.msg");
		Request req = new Request();
		req.receive(readAllData());
		assertTrue(req.newDecoder() instanceof EmptyBodyDecoder);
	}

	@Test
	public void testContentLengthDecoder() throws Exception {
		loadData("/post-request-large.msg");
		Request req = new Request();
		req.receive(readAllData());
		assertTrue(req.newDecoder() instanceof ContentLengthDecoder);
	}

	@Test
	public void testEosDecoder() throws Exception {
		loadData("/post-request-http10.msg");
		Request req = new Request();
		req.receive(readAllData());
		assertTrue(req.newDecoder() instanceof EosDecoder);
	}

	@Test
	public void testChunkingDecoder() throws Exception {
		loadData("/post-request-chunked.msg");
		Request req = new Request();
		for(ByteBuffer data : readMultiple(422, 206, 1031, 307, 2055)){
			req.receive(data);
		}
		req.receive(newChunk(ByteBuffer.allocate(0)));
		assertTrue(req.newDecoder() instanceof ChunkingDecoder);
	}

	private ByteBuffer newChunk(ByteBuffer data) {
		ByteBuffer chunk = ByteBuffer.allocate(10 + data.remaining());
		System.out.println("sending chunk header: "
				+ Integer.toHexString(data.remaining()) + "\\r\\n");
		chunk.put(Integer.toHexString(data.remaining()).getBytes());
		chunk.put(Constants.CRLF_BYTES);
		chunk.put(data);
		chunk.put(Constants.CRLF_BYTES);
		chunk.flip();
		return chunk;
	}
}
