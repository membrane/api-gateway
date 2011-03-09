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
import java.io.InputStream;
import java.util.Arrays;

import junit.framework.TestCase;

import com.predic8.membrane.core.http.AbstractBody;

@SuppressWarnings("unused")
public class BodyTest extends TestCase {

	/*
	 * @see TestCase#setUp()
	 */	
	private static byte[] msg0 = new byte[] { 'd', 'd', 13, 10, 13, 10 };
	private static byte[] msg1 = new byte[] { 'd', 'd', 13, 10, 13, 10, 'f',
			'r' };
	private static byte[] msg2 = new byte[10000];
	private static byte[] msg3 = new byte[] { 'd', 'd', 13, 13, 10 };

	private static String chunk = "1aa\r\n<?xml version='1.0' encoding='utf-8'?><soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'><soapenv:Body><ns1:getBankResponse xmlns:ns1='http://thomas-bayer.com/blz/'><ns1:details><ns1:bezeichnung>Deutsche Bank Privat und Geschäftskunden</ns1:bezeichnung><ns1:bic>DEUTDEDB380</ns1:bic><ns1:ort>Bonn</ns1:ort><ns1:plz>53004</ns1:plz></ns1:details></ns1:getBankResponse></soapenv:Body></soapenv:Envelope>\r\n0\r\n";

	private static String chunk1 = "7\r\naaaaaaa\r\n0\r\n";
	private static String chunk1Body = "aaaaaaa";

	private static String chunk2 = "2\r\naa\r\n3\r\nbbb\r\n0\r\n";
	private static String chunk2Body = "aabbb";

	private static AbstractBody body1;
	private static AbstractBody body2;

	protected void setUp() throws Exception {
		Arrays.fill(msg2, (byte) 20);
		body1 = new Body(new ByteArrayInputStream(msg1), msg1.length);
		body2 = new Body(new ByteArrayInputStream(msg2), msg2.length);
	}

//	public void testWrite() throws IOException {
//
//		ByteArrayOutputStream out = new ByteArrayOutputStream(100);
//		body1.write(out, false);
//		assertEquals(new String(out.toByteArray()), new String(msg1));
//
//		ByteArrayOutputStream out2 = new ByteArrayOutputStream(10000);
//		body2.write(out2, false);
//		assertEquals(new String(out2.toByteArray()), new String(msg2));
//	}
//
//	public void testWriteChunked() throws IOException {
//		ByteArrayOutputStream os = new ByteArrayOutputStream();
//		body1.write(os, true);
//		String s = os.toString();
//		assertEquals(16, s.length());
//	}
//
//	public void testGetLength() {
//		assertEquals(8, body1.getLength());
//		assertEquals(10000, body2.getLength());
//	}

	public void testCreateBodyFromChunk() throws Exception {
		InputStream stream = new ByteArrayInputStream(chunk.getBytes());
		AbstractBody body = new ChunkedBody(stream);

		stream = new ByteArrayInputStream(chunk1.getBytes());
		body = new ChunkedBody(stream);
		assertTrue(Arrays.equals(body.getContent(), chunk1Body.getBytes()));

		stream = new ByteArrayInputStream(chunk2.getBytes());
		body = new ChunkedBody(stream);
		
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < body.getContent().length; i ++) {
			buf.append((char)body.getContent()[i]);
		}
		
		buf = new StringBuffer();
		for (int i = 0; i < chunk2Body.getBytes().length; i ++) {
			buf.append((char)chunk2Body.getBytes()[i]);
		}
		
		assertTrue(Arrays.equals(body.getContent(), chunk2Body.getBytes()));
	
	}
	
	public void testStringConstructor() throws Exception {
		AbstractBody body = new Body("mmebrane Monitor is Cool");
		assertEquals(24, body.getContent().length);
		assertEquals(24, body.getLength());
	}

//	public void testReadChunkSize() throws Exception {
//		InputStream stream = new ByteArrayInputStream(chunk.getBytes());
//		Body body = new Body();
//		int size = body.readChunkSize(stream);
//		assertEquals(426, size);
//
//		stream = new ByteArrayInputStream(chunk1.getBytes());
//		size = body.readChunkSize(stream);
//		assertEquals(7, size);
//
//		Arrays.equals(body.getBody(), chunk1Body.getBytes());
//
//		stream = new ByteArrayInputStream(chunk2.getBytes());
//		size = body.readChunkSize(stream);
//	}

//	public void testMultipleRead() throws Exception {
//		InputStream io1 = body1.getBodyAsStream();
//		InputStream io2 = body1.getBodyAsStream();
//
//		int c1 = io1.available();
//		int c2 = io2.available();
//
//		assertTrue(c1 == c2);
//		System.out.println();
//		while (c1 > 0) {
//			System.out.print(io1.read() + " # ");
//			c1--;
//		}
//		System.out.println();
//		while (c2 > 0) {
//			System.out.print(io2.read() + " # ");
//			c2--;
//		}
//		System.out.println();
//		System.out.println(body1.getBodyAsStream());
//		System.out.println(body1.getBodyAsStream());
//	}

}
