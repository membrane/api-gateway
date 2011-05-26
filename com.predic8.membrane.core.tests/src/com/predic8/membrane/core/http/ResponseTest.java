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

import junit.framework.TestCase;

import org.junit.Test;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.EndOfStreamException;

public class ResponseTest extends TestCase {

	private Response res1;
	
	private Response res2;
	
	private Response res3;
	
	private InputStream in1;
	
	private InputStream in2;
	
	private InputStream in3;
	
	private ByteArrayOutputStream tempOut;
	
	private InputStream tempIn;
	
	@Override
	protected void setUp() throws Exception {
		in1 = ResponseTest.this.getClass().getClassLoader().getResourceAsStream("response-unchunked-html.msg");
		in2 = ResponseTest.this.getClass().getClassLoader().getResourceAsStream("response-unchunked-image.msg");
		in3 = ResponseTest.this.getClass().getClassLoader().getResourceAsStream("response-chunked-html.msg");
		
		res1 = new Response();
		res2 = new Response();
		res3 = new Response();
	}

	@Override
	protected void tearDown() throws Exception {
		if (in1 != null) {
			in1.close();
		}
		
		if (in2 != null) {
			in2.close();
		}
		
		if (in3 != null) {
			in3.close();
		}
		
		if (tempOut != null)
			tempOut.close();
		
		if (tempIn != null)
			tempIn.close();
	}
	
	public void testParseStartLine1() throws IOException, EndOfStreamException {
		res1.parseStartLine(in1);
		assertEquals(200, res1.getStatusCode());
		assertEquals("1.1", res1.getVersion());
	}
	
	public void testParseStartLine2() throws IOException, EndOfStreamException {
		res2.parseStartLine(in2);
		assertEquals(200, res2.getStatusCode());
		assertEquals("1.1", res2.getVersion());
	}
	
	public void testParseStartLine3() throws IOException, EndOfStreamException {
		res3.parseStartLine(in3);
		assertEquals(200, res3.getStatusCode());
		assertEquals("1.1", res3.getVersion());
	}
	
	@Test
	public void testUnchunkedHtmlRead() throws Exception {
		res1.read(in1, true);
		assertEquals(200, res1.getStatusCode()); 
		assertEquals(true, res1.isHTTP11());
		assertEquals(true, res1.isKeepAlive());
		assertNotNull(res1.getBody());
		assertEquals(6122, res1.getBody().getLength());
		assertEquals(6122, res1.getHeader().getContentLength());
	}
	
	@Test
	public void testUnchunkedHtmlWrite() throws Exception {
		tempOut = new ByteArrayOutputStream();
		res1.read(in1, true);
		res1.write(tempOut);
		
		
		tempIn = new ByteArrayInputStream(tempOut.toByteArray());
		
		Response resTemp = new Response();
		resTemp.read(tempIn, true);
		
		assertEquals(res1.getStatusCode(), resTemp.getStatusCode());
		assertEquals(res1.getStatusMessage(), resTemp.getStatusMessage());
		assertTrue(Arrays.equals(res1.getBody().getContent(), resTemp.getBody().getContent()));	
		assertTrue(Arrays.equals(res1.getBody().getRaw(), resTemp.getBody().getRaw()));	
	}
	
	@Test
	public void testUnchunkedImageRead() throws Exception {
		res2.read(in2, true);
		assertEquals(200, res2.getStatusCode()); 
		assertEquals(true, res2.isHTTP11());
		assertEquals(true, res2.isKeepAlive());
		assertNotNull(res2.getBody());
		assertEquals(21621, res2.getBody().getLength());
		assertEquals(21621, res2.getHeader().getContentLength());
	}
		
	
	@Test
	public void testUnchunkedImageWrite() throws Exception {
		tempOut = new ByteArrayOutputStream();
		res2.read(in2, true);
		res2.write(tempOut);
		
		
		tempIn = new ByteArrayInputStream(tempOut.toByteArray());
		
		Response resTemp = new Response();
		resTemp.read(tempIn, true);
		
		assertEquals(res2.getStatusCode(), resTemp.getStatusCode());
		assertEquals(res2.getStatusMessage(), resTemp.getStatusMessage());
		
		assertEquals(res2.getBody().getContent().length, resTemp.getBody().getContent().length);
		assertTrue(Arrays.equals(res2.getBody().getContent(), resTemp.getBody().getContent()));
	}
	
	
	@Test
	public void testChunkedHtmlRead() throws Exception {
		res3.read(in3, true);
		assertEquals(200, res3.getStatusCode()); 
		assertEquals(true, res3.isHTTP11());
		assertEquals(true, res3.isKeepAlive());
		assertNotNull(res3.getBody());	
	}
		
	
	@Test
	public void testChunkedHtmlWrite() throws Exception {
		tempOut = new ByteArrayOutputStream();
		res3.read(in3, true);
		res3.write(tempOut);
		
		
		tempIn = new ByteArrayInputStream(tempOut.toByteArray());
		
		Response resTemp = new Response();
		resTemp.read(tempIn, true);
		
		assertEquals(res3.getStatusCode(), resTemp.getStatusCode());
		assertEquals(res3.getStatusMessage(), resTemp.getStatusMessage());
		
		assertEquals(res3.getBody().getContent().length, resTemp.getBody().getContent().length);
		assertTrue(Arrays.equals(res3.getBody().getContent(), resTemp.getBody().getContent()));
	}
	
	@Test
	public void testWithNoContentLength() throws Exception {
		InputStream in = ResponseTest.this.getClass().getClassLoader().getResourceAsStream("response-no-content-length.txt");
		res3.read(in, true);
		assertEquals(185, res3.getBody().getLength());
	}
}
