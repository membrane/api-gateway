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

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.StringTestUtil.*;
import static com.predic8.membrane.test.TestUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

	private static final Request request = new Request();

	private InputStream inPost;

	private InputStream inChunked;

	private ByteArrayOutputStream tempOut;

	private InputStream tempIn;

	private static final InputStream isPOSTStartLine = new ByteArrayInputStream(("POST /foo HTTP/1.1" + CRLF).getBytes(UTF_8));
	private static final InputStream isPosTStartLine = new ByteArrayInputStream(("PosT /foo HTTP/1.1" + CRLF).getBytes(UTF_8));
	private static final InputStream isLowercaseMethodStartLine = new ByteArrayInputStream(("get /foo HTTP/1.1" + CRLF).getBytes(UTF_8));
	private static final InputStream isProxyStartLine = new ByteArrayInputStream(("GET http://example.com/foo HTTP/1.0" + CRLF).getBytes(UTF_8));

	@BeforeEach
	void setUp() {
		inPost = getResourceAsStream(this,"request-post.msg");
		inChunked = getResourceAsStream(this,"request-chunked-soap.msg");
	}

	@AfterEach
	void tearDown() throws Exception {

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

	@Nested
	class StartLine {

		@Test
		void chunked() throws IOException {
			request.parseStartLine(inChunked);
			assertTrue(request.isPOSTRequest());
			assertEquals("/axis2/services/BLZService", request.getUri());
			assertEquals("1.1", request.getVersion());
		}

		@Test
		void post() throws IOException {
			request.parseStartLine(isPOSTStartLine);
			assertTrue(request.isPOSTRequest());
			assertEquals("1.1", request.getVersion());
			assertEquals("/foo", request.getUri());
		}

		@Test
		void postWrongCasing() throws IOException {
			request.parseStartLine(isPosTStartLine);
			assertEquals("PosT",request.getMethod());
			assertEquals("1.1", request.getVersion());
			assertEquals("/foo", request.getUri());
		}

		@Test
		void lowerCaseMethod() throws IOException {
			request.parseStartLine(isLowercaseMethodStartLine);
			assertEquals("get",request.getMethod());
		}

		@Test
		void absoluteFormForProxying() throws IOException {
			request.parseStartLine(isProxyStartLine);
			assertEquals("GET",request.getMethod());
			assertEquals("1.0", request.getVersion());
			assertEquals("http://example.com/foo", request.getUri());
		}

	}

	@Test
	void readChunked() throws Exception {
		request.read(inChunked, true);
		assertNotNull(request.getBodyAsStream());
	}

	@Test
	void readPost() throws Exception {
		request.read(inPost, true);
		assertEquals(METHOD_POST, request.getMethod());
		assertEquals("/operation/call", request.getUri());
		assertNotNull(request.getBody());

		assertEquals(168, request.getBody().getLength());
	}

	@Test
	void writePost() throws Exception {
		request.read(inPost, true);

		tempOut = new ByteArrayOutputStream();
		request.write(tempOut, true);

		tempIn = new ByteArrayInputStream(tempOut.toByteArray());

		Request reqTemp = new Request();
		reqTemp.read(tempIn, true);

		assertEquals(request.getUri(), reqTemp.getUri());
		assertEquals(request.getMethod(), reqTemp.getMethod());

		assertArrayEquals(request.getBody().getContent(), reqTemp.getBody().getContent());
		assertArrayEquals(request.getBody().getRaw(), reqTemp.getBody().getRaw());
	}

	@Test
	void isHTTP11() {
		assertTrue(request.isHTTP11());
	}

	@Test
	void isHTTP11Chunked() {
		assertTrue(request.isHTTP11());
	}

	@Test
	void isKeepAlive() {
		assertTrue(request.isKeepAlive());
	}

	@Test
	void isKeepAliveChunked() {
		assertTrue(request.isKeepAlive());
	}

	@Test
	void isEmpty() throws IOException, URISyntaxException {
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

	/**
	 * If we replace the body, the original body should be read, to make sure there is nothing left
	 * in the inputStream that can be read as part of the next message in an keep alive session.
	 */
	@Test
	void setBodyShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
		AbstractBody originalBody = readMessageAndGetBody();
		request.setBody(new Body("ABC".getBytes(UTF_8))); // Replace body with a different one
		assertTrue(originalBody.isRead()); // Assert that the original body is read
	}

	@Test
	void optionsWithBodyContentLength() throws EndOfStreamException, IOException {
		shouldBodyBeRead("""
				OPTIONS /products HTTP/1.1
				Content-Length: 5
				Origin: https://predic8.de
				
				Dummy
				""", true);
	}

	@Test
	void optionsWithBody() throws EndOfStreamException, IOException {
		shouldBodyBeRead("""
				OPTIONS /products HTTP/1.1
				Transfer-Encoding: chunked
				Origin: https://predic8.de
			
				Dummy
				""", true);
	}

	@Test
	void optionsWithoutBody() throws EndOfStreamException, IOException {
        shouldBodyBeRead("""
                OPTIONS /products HTTP/1.1
                Origin: https://predic8.de
                
                """, false);
	}

	private static void shouldBodyBeRead(String message, boolean expect) throws IOException, EndOfStreamException {
		Request req = new Request();
		req.read(new ByteArrayInputStream(message.getBytes(UTF_8)), true);
		assertEquals(expect, !req.shouldNotContainBody());
	}

	/**
	 * Same as setBodyShouldReadTheOriginalBody test but with Request.setBodyContent
	 */
	@Test
	void setBodyContentShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
		AbstractBody originalBody = readMessageAndGetBody();
		request.setBodyContent("ABC".getBytes(UTF_8));
		assertTrue(originalBody.isRead()); // Assert that the original body is read
		assertEquals(0,inPost.available()); // Check that all bytes are read from the stream
	}

	@Test
	void connectUsesAuthorityForm() throws URISyntaxException {
        assertEquals("CONNECT example.com:443 HTTP/1.1" + CRLF, connect("https://example.com:443").build().getStartLine());
	}

	@Test
	void isXML() throws URISyntaxException {
        assertTrue(post("/foo").contentType(TEXT_XML).build().isXML());
		assertTrue(post("/foo").contentType("text/xml; charset=utf-8").build().isXML());
		assertTrue(post("/foo").header("Content-Type", "text/xml; charset=utf-8").build().isXML());
	}

	private AbstractBody readMessageAndGetBody() throws IOException, EndOfStreamException {
		request.read(inPost, true);
		assertFalse(request.getBody().isRead());
        return request.getBody();
	}
}