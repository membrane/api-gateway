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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

import junit.framework.TestCase;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.EndOfStreamException;


public class RequestTest extends TestCase {

	private static String httpHeader = "POST /axis/services/version HTTP/1.1" + Constants.CRLF
			+ "Content-Type: text/plain;charset=utf-8" + Constants.CRLF
			+ "Content-Length: 6" + Constants.CRLF
			+ "Date: Thu, 22 Jul 2004 19:18:11 GMT" + Constants.CRLF
			+ "Server: Apache-Coyote/1.1" + Constants.CRLF  
			+ "Connection: close" + Constants.CRLF
			+ Constants.CRLF + "Hello!" + Constants.CRLF;
	
	private static String requestLine = "POST /axis/services/version HTTP/1.1" + Constants.CRLF;
	
	private static Request req1 = new Request();
	
	private static Request req2 = new Request();
	
	private static Request reqChunked = new Request();
	
	private InputStream in1;
	
	private InputStream in2;
	
	private InputStream inChunkedSoap;
	
	private ByteArrayOutputStream tempOut;
	
	private InputStream tempIn;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		in1 = new ByteArrayInputStream(requestLine.getBytes());
		in2 = getClass().getClassLoader().getResourceAsStream("request-post.msg");
		inChunkedSoap = getClass().getClassLoader().getResourceAsStream("request-chunked-soap.msg");
	}

	@Override
	protected void tearDown() throws Exception {
		if (in1 != null)
			in1.close();
		
		if (in2 != null) {
			in2.close();
		}
		
		if (inChunkedSoap != null) {
			inChunkedSoap.close();
		}
		
		if (tempIn != null) {
			tempIn.close();
		}
		
		if (tempOut != null) {
			tempOut.close();
		}
		
		
	}
	
	@Test
	public void testParseStartLine() throws IOException, EndOfStreamException {
		req1.parseStartLine(in1);
		assertEquals("POST", req1.getMethod());
		assertEquals("/axis/services/version", req1.getUri());
		assertEquals("1.1", req1.getVersion());
	}
	
	@Test
	public void testParseStartLineChunked() throws IOException, EndOfStreamException {
		reqChunked.parseStartLine(inChunkedSoap);
		assertEquals("POST", reqChunked.getMethod());
		assertEquals("/axis2/services/BLZService", reqChunked.getUri());
		assertEquals("1.1", reqChunked.getVersion());
	}
	
	@Test
	public void testRead() throws IOException, EndOfStreamException {
		req1.read(new ByteArrayInputStream(httpHeader.getBytes()), true);
		assertEquals(Request.METHOD_POST, req1.getMethod());
		assertEquals("/axis/services/version", req1.getUri());
	}
	
	@Test
	public void testReadChunked() throws Exception {
		reqChunked.read(inChunkedSoap, true);
		assertEquals(Request.METHOD_POST, reqChunked.getMethod());
		InputStream istr = reqChunked.getBodyAsStream();
		assertNotNull(istr);
	}
	
	@Test
	public void testReadPost() throws Exception {
		req2.read(in2, true);
		assertEquals(Request.METHOD_POST, req2.getMethod());
		assertEquals("/operation/call", req2.getUri());
		assertNotNull(req2.getBody());
		
		assertEquals(168, req2.getBody().getLength());
	}
	
	@Test
	public void testWritePost() throws Exception {
		tempOut = new ByteArrayOutputStream();
		req2.read(in2, true);
		req2.write(tempOut);
		
		tempIn = new ByteArrayInputStream(tempOut.toByteArray());
		
		Request reqTemp = new Request();
		reqTemp.read(tempIn, true);
		
		assertEquals(req2.getUri(), reqTemp.getUri());
		assertEquals(req2.getMethod(), reqTemp.getMethod());
		
		assertTrue(Arrays.equals(req2.getBody().getContent(), reqTemp.getBody().getContent()));	
		assertTrue(Arrays.equals(req2.getBody().getRaw(), reqTemp.getBody().getRaw()));
	}

	@Test
	public void testGetMethod() throws Exception {
		assertEquals("POST", req1.getMethod());
	}
	
	@Test
	public void testGetUri() throws Exception {
		assertEquals("/axis/services/version",req1.getUri());
	}

	@Test
	public void testIsHTTP11() throws Exception {
		assertTrue(req2.isHTTP11());
	}
	
	@Test
	public void testIsHTTP11Chunked() throws Exception {
		assertTrue(reqChunked.isHTTP11());
	}
	
	@Test
	public void testIsKeepAlive() throws Exception {
		req2.read(in2, true);
		assertTrue(req2.isKeepAlive());
	}
	
	@Test
	public void testIsKeepAliveChunked() throws Exception {
		reqChunked.read(inChunkedSoap, true);
		assertTrue(reqChunked.isKeepAlive());
	}
	

}