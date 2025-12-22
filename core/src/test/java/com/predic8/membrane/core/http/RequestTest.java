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
import org.jetbrains.annotations.NotNull;
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

	private static final Request reqPost = new Request();

	private static final Request reqChunked = new Request();

	private InputStream inPost;

	private InputStream inEmptyPost;

	private InputStream inChunked;

	private ByteArrayOutputStream tempOut;

	private InputStream tempIn;

	private static final String POST_REQUEST = """
		POST /operation/call HTTP/1.1
		Host: service-repository.com:80
		Connection: keep-alive
		User-Agent: Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/530.5 (KHTML, like Gecko) Chrome/2.0.172.37 Safari/530.5
		Referer: http://sr/operation/show?operation=getBank&portType=BLZServicePortType&id=65657
		Content-Length: 168
		Cache-Control: max-age=0
		Origin: http://sr
		Content-Type: application/x-www-form-urlencoded
		Accept: application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5
		Accept-Encoding: gzip,deflate,sdch
		Cookie: JSESSIONID=E431BCAA27640D0629149A89451239F7
		Accept-Language: de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4
		Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.3
		
		endpoint=http%3A%2F%2Fwww.thomas-bayer.com%3A80%2Faxis2%2Fservices%2FBLZService&xpath%3A%2FgetBank%2Fblz=38070024&id=65657&operation=getBank&portType=BLZServicePortType
		""";

	private static final String CHUNKED_REQUEST = """
		POST /axis2/services/BLZService HTTP/1.1
		Content-Type: application/soap+xml; charset=UTF-8; action="http://thomas-bayer.com/blz/BLZServicePortType/getBankRequest"
		User-Agent: Axis2
		Host: localhost:7000
		Transfer-Encoding: chunked
		
		ff
		<?xml version='1.0' encoding='UTF-8'?><soapenv:Envelope xmlns:soapenv="http://www.w3.org/2003/05/soap-envelope"><soapenv:Body><ns1:getBank xmlns:ns1="http://thomas-bayer.com/blz/"><ns1:blz>66762332</ns1:blz></ns1:getBank></soapenv:Body></soapenv:Envelope>
		0
		""";

	private static final String POST_EMPTY_BODY_REQUEST = """
		POST /operation/call HTTP/1.1
		Host: service-repository.com:80
		Connection: close
		User-Agent: test-client/1.0
		Content-Length: 0
		Content-Type: application/x-www-form-urlencoded
		Accept: */*
		
		""";

	@BeforeEach
	public void setUp() {
		inPost = getRequest(POST_REQUEST);
		inEmptyPost = new ByteArrayInputStream(POST_EMPTY_BODY_REQUEST.stripIndent().replace("\n", "\r\n").getBytes());
		inChunked = getRequest(CHUNKED_REQUEST);
	}

	private static @NotNull ByteArrayInputStream getRequest(String request) {
		return new ByteArrayInputStream(request.stripIndent().stripTrailing().replace("\n", "\r\n").getBytes());
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
	void readEmptyPost() throws Exception {
		reqPost.read(inEmptyPost, true);
		assertEquals(METHOD_POST, reqPost.getMethod());
		assertEquals("/operation/call", reqPost.getUri());
		assertNotNull(reqPost.getBody());

		assertEquals(0, reqPost.getBody().getLength());
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
		assertEquals(0, inPost.available()); // Check that all bytes are read from the stream
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
}