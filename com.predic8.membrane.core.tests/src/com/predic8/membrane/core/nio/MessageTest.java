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

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;

import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.Test;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Header;

/**
 * 
 */
public class MessageTest extends NioTestBase {
	
	private static Logger log = Logger.getLogger(MessageTest.class);

	@Test
	public void testRequestGetStatusline() throws Exception {
		loadData("/get-request.msg");
		Request req = new Request();
		sendAllData(req);
		assertEquals("GET /foo?bar=baz HTTP/1.1\r\n", req.getStatusline());
	}

	@Test
	public void testResponseGetStatusline() throws Exception {
		Response resp = Response.getServerErrorResponse("");
		assertEquals("HTTP/1.1 500 Internal Server Error\r\n",
				resp.getStatusline());
	}

	@Test
	public void testEmptyRequest() throws Exception {
		Request req = new Request();
		assertEquals(0, req.length());
		assertEquals(0, req.tell());
		assertFalse(req.isHeaderRead());
		try {
			req.getHeader();
			fail();
		} catch (RuntimeException e) {
			assertEquals("Header not yet read.", e.getMessage());
		}
		assertEquals("1.1", req.getHttpVersion());
		assertEquals("GET", req.getMethod());
		assertNull(req.getUri());
		assertEquals("GET null HTTP/1.1" + Constants.CRLF, req.getStatusline());
		assertFalse(req.isHttp10());
		assertTrue(req.isHttp11());
		assertEquals(0, req.getBodyLength());
		assertFalse(req.isBodyComplete());
		try {
			req.newDecoder();
			fail();
		} catch (NullPointerException e) {
		} catch (RuntimeException e) {
			assertEquals("Header not yet read.", e.getMessage());
		}
	}

	@Test
	public void testGetRequestSingleBuffer() throws Exception {
		loadData("/get-request.msg");
		Request req = new Request();
		assertEquals(0, req.length());
		sendAllData(req);
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
		int bytesSentSoFar = 0;
		for (int size : new int[] { 50, 200, 1024, 300, 2048 }) {
			bytesSentSoFar += size;
			send(req, size);
			assertEquals(bytesSentSoFar, req.length());
		}
		assertTrue(req.isHeaderRead());
		assertNotNull(req.getHeader());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(4674, header.getContentLength());
		assertEquals("application/xml", header.getContentType());
		assertFalse(req.isBodyComplete());
		sendAllData(req);
		assertEquals(getDataLength(), req.length());
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testPostRequestHttp10() throws Exception {
		loadData("/post-request-http10.msg");
		Request req = new Request();
		int bytesSentSoFar = 0;
		for (int size : new int[] { 50, 200, 1024, 300, 2048 }) {
			bytesSentSoFar += size;
			send(req, size);
			assertEquals(bytesSentSoFar, req.length());
		}
		assertTrue(req.isHeaderRead());
		assertTrue(req.isHttp10());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertEquals(-1, header.getContentLength());
		assertFalse(req.isBodyComplete());
		sendAllData(req);
		req.handleRead(requestData);
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testPostRequestChunked() throws Exception {
		loadData("/post-request-chunked.msg");
		Request req = new Request();
		int bytesSentSoFar = 422;
		send(req, 422); // send headers
		assertTrue(req.isHeaderRead());
		Header header = req.getHeader();
		assertEquals("www.example.com", header.getHost());
		assertTrue(header.isChunked());
		assertEquals(-1, header.getContentLength());
		assertFalse(req.isBodyComplete());
		while (requestData.position() < requestData.size()) {
			int numBytes = random.nextInt(2048);
			log.debug("sending " + numBytes + " bytes.");
			bytesSentSoFar += send(req, numBytes);
			assertEquals(bytesSentSoFar, req.getRawBodyLength() + 422);
		}
		assertTrue(req.isBodyComplete());
	}

	@Test
	public void testEmptyBodyDecoder() throws Exception {
		loadData("/get-request.msg");
		Request req = new Request();
		sendAllData(req);
		assertTrue(req.newDecoder() instanceof EmptyBodyDecoder);
	}

	@Test
	public void testContentLengthDecoder() throws Exception {
		loadData("/post-request-large.msg");
		Request req = new Request();
		sendAllData(req);
		assertTrue(req.newDecoder() instanceof ContentLengthDecoder);
	}

	@Test
	public void testEosDecoder() throws Exception {
		loadData("/post-request-http10.msg");
		Request req = new Request();
		sendAllData(req);
		assertTrue(req.newDecoder() instanceof EosDecoder);
	}

	@Test
	public void testChunkingDecoder() throws Exception {
		loadData("/post-request-chunked.msg");
		Request req = new Request();
		sendAllData(req);
		assertTrue(req.newDecoder() instanceof ChunkingDecoder);
	}

	@Test
	public void testSeekAndTell() throws Exception {
		loadData("/post-request-large.msg");
		Request req = new Request();

		sendAllData(req);
		assertEquals(0, req.tell());

		int bodyStartsAtByte = req.getStatusline().length()
				+ req.getHeader().toString().length()
				+ Constants.CRLF_BYTES.length;

		for (int i = 0; i < 500; i++) {
			int newpos = random.nextInt(getDataLength() - bodyStartsAtByte);
			req.seek(newpos);
			assertEquals(newpos, req.tell());
			requestData.position(bodyStartsAtByte + newpos);
			ByteBuffer soll = read(100);
			ByteBuffer ist = req.get(100);
			assertEquals(newpos + ist.remaining(), req.tell());
			assertEquals(soll, ist);
			req.rewind();
			assertEquals(0, req.tell());
		}
		try {
			req.seek(getDataLength() - bodyStartsAtByte + 1);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("cannot skip past end-of-data.", e.getMessage());
		}
	}

	@Ignore
	@Test
	public void testPickupRequest() throws Exception {
		loadData("/post-request-large.msg");
		Request req = new Request();
		sendAllData(req);

		int bytesRead = 0;
		ByteBuffer headers = ByteBuffer.allocate(req.tell());
		req.pickup(headers);
		headers.flip();
		bytesRead += headers.remaining();
		assertEquals(req.getStatusline().length()
				+ req.getHeader().toString().length() + 2, headers.remaining());
		ByteBuffer payload = ByteBuffer.allocate(300);
		while (true) {
			payload.clear();
			req.pickup(payload);
			payload.flip();
			bytesRead += payload.remaining();
			if (payload.remaining() == 0)
				break;
		}
		assertEquals(req.length(), bytesRead);
	}

	@Test
	public void testClearBody() throws Exception {
		loadData("/post-request-http10.msg");
		Request req = new Request();
		sendAllData(req);
		req.handleRead(requestData);

		assertTrue(req.isBodyComplete());
		assertEquals(100, req.get(100).remaining());
		assertEquals(5023, req.length());

		req.clearBody();

		assertEquals(0, req.tell());
		assertFalse(req.isBodyComplete());
		assertEquals(0, req.get(100).remaining());
		assertEquals(349, req.length());
	}

	@Ignore
	@Test
	public void testGetServerErrorResponse() throws Exception {
		Response err = Response
				.getServerErrorResponse("Something aweful happened. Don't ask me, what!");
		assertEquals(500, err.getStatusCode());
		assertEquals("Internal Server Error", err.getStatusMessage());
		assertEquals("HTTP/1.1 500 Internal Server Error" + Constants.CRLF,
				err.getStatusline());
		Header header = err.getHeader();
		assertEquals("text/html;charset=utf-8", header.getContentType());
		assertEquals("Membrane-Monitor " + Constants.VERSION,
				header.getFirstValue("Server"));
		assertEquals("close", header.getFirstValue("Connection"));
		// assertEquals(123, err.length());

		ByteBuffer errData = ByteBuffer.allocate(err.length());
		// two calls: first one only retrieves the headers
		err.pickup(errData);
		err.pickup(errData);
		errData.flip();
		assertEquals(err.length(), errData.remaining());
		Channels.newChannel(System.out).write(errData);
	}

	@SuppressWarnings("unused")
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
