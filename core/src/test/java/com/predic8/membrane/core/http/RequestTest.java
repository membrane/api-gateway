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

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.StringTestUtil.*;
import static com.predic8.membrane.util.TestUTtil2.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

	private static final Request reqPost = new Request();

	private static final Request reqChunked = new Request();

	private InputStream inPost;

	private InputStream inChunked;

	private ByteArrayOutputStream tempOut;

	private InputStream tempIn;

	@BeforeEach
	public void setUp() throws Exception {
		inPost = getResourceAsStream(this,"request-post.msg");
		inChunked = getResourceAsStream(this,"request-chunked-soap.msg");
	}

	@AfterEach
	public void tearDown() throws Exception {

		if (inPost != null) {
			inPost.close();
		}

		if (inChunked != null) {
			inChunked.close();
		}

		if (tempIn != null) {
			tempIn.close();
		}

		if (tempOut != null) {
			tempOut.close();
		}

	}
	
	@Test
	public void testParseStartLineChunked() throws IOException, EndOfStreamException {
		reqChunked.parseStartLine(inChunked);
		assertTrue(reqChunked.isPOSTRequest());
		assertEquals("/axis2/services/BLZService", reqChunked.getUri());
		assertEquals("1.1", reqChunked.getVersion());
	}

	@Test
	public void testReadChunked() throws Exception {
		reqChunked.read(inChunked, true);
		assertNotNull(reqChunked.getBodyAsStream());
	}

	@Test
	public void testReadPost() throws Exception {
		reqPost.read(inPost, true);
		assertEquals(METHOD_POST, reqPost.getMethod());
		assertEquals("/operation/call", reqPost.getUri());
		assertNotNull(reqPost.getBody());

		assertEquals(168, reqPost.getBody().getLength());
	}

	@Test
	public void testWritePost() throws Exception {
		reqPost.read(inPost, true);

		tempOut = new ByteArrayOutputStream();
		reqPost.write(tempOut, true);

		tempIn = new ByteArrayInputStream(tempOut.toByteArray());

		Request reqTemp = new Request();
		reqTemp.read(tempIn, true);

		assertEquals(reqPost.getUri(), reqTemp.getUri());
		assertEquals(reqPost.getMethod(), reqTemp.getMethod());

		assertArrayEquals(reqPost.getBody().getContent(), reqTemp.getBody().getContent());
		assertArrayEquals(reqPost.getBody().getRaw(), reqTemp.getBody().getRaw());
	}

	@Test
	public void testIsHTTP11() {
		assertTrue(reqPost.isHTTP11());
	}

	@Test
	public void testIsHTTP11Chunked() {
		assertTrue(reqChunked.isHTTP11());
	}

	@Test
	public void testIsKeepAlive() {
		assertTrue(reqPost.isKeepAlive());
	}

	@Test
	public void testIsKeepAliveChunked() {
		assertTrue(reqChunked.isKeepAlive());
	}

	@Test
	public void isEmpty() throws IOException, URISyntaxException {
		assertTrue(new Builder().body("").build().isBodyEmpty());
		assertTrue(new Builder().body("".getBytes(UTF_8)).build().isBodyEmpty());
		assertTrue(get("/foo").build().isBodyEmpty());
	}

	@Test
	void isNotEmpty() throws IOException, URISyntaxException {
		assertFalse(post("/foo").body("ABC").build().isBodyEmpty());
	}

	@Test
	void createFromStream() throws IOException {
		Request req = new Request();
		req.create("POST", "http://test", "HTTP/", new Header(), getResourceAsStream(this,"/getBank.xml"));
		assertFalse(req.isBodyEmpty());
	}

	@Test
	void createFromStreamMethodGETDoNotSupportBody() throws IOException {
		Request req = new Request();
		req.create(METHOD_GET , "http://test", "HTTP/", new Header(), getResourceAsStream(this,"/getBank.xml"));
		assertTrue(req.isBodyEmpty());
	}

	@Test
	void createFromStreamMethodHEADDoNotSupportBody() throws IOException {
		Request req = new Request();
		req.create(METHOD_HEAD, "http://test", "HTTP/", new Header(), getResourceAsStream(this,"/getBank.xml"));
		assertTrue(req.isBodyEmpty());
	}
	
	@Test
	void addHeaderToExisting() throws IOException, EndOfStreamException {
		Request req = new Request();
		req.read(inputStreamFrom("""
                GET / HTTP/1.1
                Foo: 1
                Foo: 2
    			
                """),true);
		req.getHeader().add("Foo","3"); // Now add a third and see if the sequence is kept.

		assertEquals("1,2,3",req.getHeader().getNormalizedValue("Foo"));
	}

}