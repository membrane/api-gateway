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

import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.time.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.CONTENT_LENGTH;
import static com.predic8.membrane.core.http.MimeType.TEXT_XML;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.util.StringTestUtil.*;
import static com.predic8.membrane.test.TestUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;

public class RequestTest {

	private static final Request reqPost = new Request();

	private static final Request reqChunked = new Request();

	private InputStream inPost;

	private InputStream inChunked;

	private ByteArrayOutputStream tempOut;

	private InputStream tempIn;

	@BeforeEach
	public void setUp() {
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
	void parseStartLineChunked() throws IOException {
		reqChunked.parseStartLine(inChunked);
		assertTrue(reqChunked.isPOSTRequest());
		assertEquals("/axis2/services/BLZService", reqChunked.getUri());
		assertEquals("1.1", reqChunked.getVersion());
	}

	@Test
	void readChunked() throws Exception {
		reqChunked.read(inChunked, true);
		assertNotNull(reqChunked.getBodyAsStream());
	}

	@Test
	void readPost() throws Exception {
		reqPost.read(inPost, true);
		assertEquals(METHOD_POST, reqPost.getMethod());
		assertEquals("/operation/call", reqPost.getUri());
		assertNotNull(reqPost.getBody());

		assertEquals(168, reqPost.getBody().getLength());
	}

	@Test
	void writePost() throws Exception {
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
	void isHTTP11() {
		assertTrue(reqPost.isHTTP11());
	}

	@Test
	void isHTTP11Chunked() {
		assertTrue(reqChunked.isHTTP11());
	}

	@Test
	void isKeepAlive() {
		assertTrue(reqPost.isKeepAlive());
	}

	@Test
	void isKeepAliveChunked() {
		assertTrue(reqChunked.isKeepAlive());
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
	void createFromStream() throws IOException, URISyntaxException {
		Request req = Request.post("http://test/")
				.header(CONTENT_LENGTH, "1")
				.body(getResourceAsStream(this,"/getBank.xml"))
				.build();
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
	 * @throws EndOfStreamException
	 * @throws IOException
	 */
	@Test
	void setBodyShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
		AbstractBody originalBody = readMessageAndGetBody();
		reqPost.setBody(new Body("ABC".getBytes(UTF_8))); // Replace body with a different one
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
	 * @throws EndOfStreamException
	 * @throws IOException
	 */
	@Test
	void setBodyContentShouldReadTheOriginalBody() throws EndOfStreamException, IOException {
		AbstractBody originalBody = readMessageAndGetBody();
		reqPost.setBodyContent("ABC".getBytes(UTF_8));
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
		reqPost.read(inPost, true);
		assertFalse(reqPost.getBody().isRead());
        return reqPost.getBody();
	}

	@Nested
	class ParseStartLine {

		@Test
		void originForm() throws IOException {
			assertStartLine("GET /foo HTTP/1.1", "GET", "/foo", "1.1");
		}

		@Test
		void queryIsPartOfUri() throws IOException {
			assertStartLine("GET /foo?a=1&b=%20 HTTP/1.1", "GET", "/foo?a=1&b=%20", "1.1");
		}

		@Test
		void absoluteForm() throws IOException {
			assertStartLine("GET http://example.com:8080/foo HTTP/1.1", "GET", "http://example.com:8080/foo", "1.1");
		}

		@Test
		void authorityForm() throws IOException {
			assertStartLine("CONNECT example.com:443 HTTP/1.1", "CONNECT", "example.com:443", "1.1");
		}

		@Test
		void asteriskForm() throws IOException {
			assertStartLine("OPTIONS * HTTP/1.1", "OPTIONS", "*", "1.1");
		}

		@Test
		void http10() throws IOException {
			assertStartLine("POST /foo HTTP/1.0", "POST", "/foo", "1.0");
		}

		@Test
		void extensionMethod() throws IOException {
			assertStartLine("QUERY /foo HTTP/1.1", "QUERY", "/foo", "1.1");
		}

		@Test
		void methodCaseIsKept() throws IOException {
			assertStartLine("get /foo HTTP/1.1", "get", "/foo", "1.1");
		}

		@Test
		void versionIsNotValidated() throws IOException {
			assertStartLine("GET /foo HTTP/abc", "GET", "/foo", "abc");
		}

		@Test
		void trailingWhitespaceIsPartOfVersion() throws IOException {
			assertStartLine("GET /foo HTTP/1.1 ", "GET", "/foo", "1.1 ");
		}

		@Test
		void spaceInUriIsPartOfUri() throws IOException {
			assertStartLine("GET /a b HTTP/1.1", "GET", "/a b", "1.1");
		}

		@Test
		void extraSpaceAfterMethodIsPartOfUri() throws IOException {
			assertStartLine("GET  /foo HTTP/1.1", "GET", " /foo", "1.1");
		}

		@Test
		void methodEndsAtFirstSpace() throws IOException {
			assertStartLine("X GET /foo HTTP/1.1", "X", "GET /foo", "1.1");
		}

		@Test
		void uriEndsAtFirstHttpVersion() throws IOException {
			assertStartLine("GET /foo HTTP/1.1 HTTP/2", "GET", "/foo", "1.1 HTTP/2");
		}

		@Test
		void bytesAreReadAsLatin1() throws IOException {
			Request req = parse("GET /ä HTTP/1.1\r\n".getBytes(UTF_8));
			assertEquals(new String("/ä".getBytes(UTF_8), ISO_8859_1), req.getUri());
		}

		@Test
		void lfLineEnding() throws IOException {
			Request req = parse("GET /foo HTTP/1.1\nHost: example.com\n".getBytes(UTF_8));
			assertEquals("/foo", req.getUri());
			assertEquals("1.1", req.getVersion());
		}

		@Test
		void consumesOnlyTheStartLine() throws IOException {
			InputStream in = new ByteArrayInputStream("GET /foo HTTP/1.1\r\nHost: example.com\r\n".getBytes(UTF_8));
			new Request().parseStartLine(in);
			assertEquals("Host: example.com\r\n", new String(in.readAllBytes(), UTF_8));
		}

		@Test
		void longUri() throws IOException {
			String uri = "/" + "a".repeat(8000);
			assertStartLine("GET " + uri + " HTTP/1.1", "GET", uri, "1.1");
		}

		@Test
		void leadingSpaceIsPartOfMethod() throws IOException {
			assertStartLine(" GET /foo HTTP/1.1", " GET", "/foo", "1.1");
		}

		@Test
		void manySpacesWithoutVersionDoNotBacktrack() {
			assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
				var line = "a ".repeat(4000);
				assertStartLine(line, line, "", "STOMP");
			});
		}

		@Test
		void manySpacesWithEmptyVersionDoNotBacktrack() {
			assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
				var line = "a ".repeat(3990) + "HTTP/";
				assertStartLine(line, line, "", "STOMP");
			});
		}

		/**
		 * Byte 0x85 is NEL, a line terminator for regex. The former regex parser stopped there and dropped or misread
		 * the parts around it.
		 */
		@Test
		void nextLineInVersionIsRejected() {
			assertRejected("GET / HTTP/1.\u00851");
		}

		@Test
		void nextLineAfterVersionIsRejected() {
			assertRejected("GET / HTTP/1.0\u0085");
		}

		@Test
		void nextLineInUriIsRejected() {
			assertRejected("GET /a\u0085b HTTP/1.1");
		}

		@Test
		void nextLineInMethodIsRejected() {
			assertRejected("X\u0085GET / HTTP/1.1");
		}

		@Test
		void lineTooLong() {
			assertThrows(LineTooLongException.class, () -> parse(("GET /" + "a".repeat(9000) + " HTTP/1.1\r\n").getBytes(UTF_8)));
		}

		@Nested
		class StompFallback {

			@Test
			void stompCommand() throws IOException {
				assertStartLine("CONNECT", "CONNECT", "", "STOMP");
			}

			@Test
			void missingVersion() throws IOException {
				assertStartLine("GET /foo", "GET /foo", "", "STOMP");
			}

			@Test
			void emptyVersion() throws IOException {
				assertStartLine("GET /foo HTTP/", "GET /foo HTTP/", "", "STOMP");
			}

			@Test
			void lowercaseProtocol() throws IOException {
				assertStartLine("GET /foo http/1.1", "GET /foo http/1.1", "", "STOMP");
			}

			@Test
			void tabSeparator() throws IOException {
				assertStartLine("GET\t/foo HTTP/1.1", "GET\t/foo HTTP/1.1", "", "STOMP");
			}
		}

		@Nested
		class EndOfStream {

			@Test
			void emptyStream() {
				assertThrows(NoMoreRequestsException.class, () -> parse(new byte[0]));
			}

			@Test
			void emptyLine() {
				assertThrows(NoMoreRequestsException.class, () -> parse("\r\n".getBytes(UTF_8)));
			}

			@Test
			void eofInsideLine() {
				EOFWhileReadingFirstLineException e = assertThrows(EOFWhileReadingFirstLineException.class,
						() -> parse("GET /foo HT".getBytes(UTF_8)));
				assertEquals("GET /foo HT", e.getLineSoFar());
			}
		}

		private static void assertStartLine(String startLine, String method, String uri, String version) throws IOException {
			Request req = parse((startLine + "\r\n").getBytes(UTF_8));
			assertEquals(method, req.getMethod());
			assertEquals(uri, req.getUri());
			assertEquals(version, req.getVersion());
		}

		private static void assertRejected(String startLine) {
			assertThrows(EOFWhileReadingFirstLineException.class, () -> parse((startLine + "\r\n").getBytes(ISO_8859_1)));
		}

		private static Request parse(byte[] startLine) throws IOException {
			Request req = new Request();
			req.parseStartLine(new ByteArrayInputStream(startLine));
			return req;
		}
	}
}