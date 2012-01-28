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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
@SuppressWarnings("unused")
public class BodyTest {

	private static byte[] msg0 = new byte[] { 'd', 'd', 13, 10, 13, 10 };
	private static byte[] msg1 = new byte[] { 'd', 'd', 13, 10, 13, 10, 'f',
			'r' };
	private static byte[] msg2 = new byte[10000];
	private static byte[] msg3 = new byte[] { 'd', 'd', 13, 13, 10 };

	private static String chunk = "1aa\r\n<?xml version='1.0' encoding='utf-8'?><soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'><soapenv:Body><ns1:getBankResponse xmlns:ns1='http://thomas-bayer.com/blz/'><ns1:details><ns1:bezeichnung>Deutsche Bank Privat und Geschaeftskunden</ns1:bezeichnung><ns1:bic>DEUTDEDB380</ns1:bic><ns1:ort>Bonn</ns1:ort><ns1:plz>53004</ns1:plz></ns1:details></ns1:getBankResponse></soapenv:Body></soapenv:Envelope>\r\n0\r\n";

	private static String chunk1 = "7\r\naaaaaaa\r\n0\r\n";
	private static String chunk1Body = "aaaaaaa";

	private static String chunk2 = "2\r\naa\r\n3\r\nbbb\r\n0\r\n";
	private static String chunk2Body = "aabbb";

	private static AbstractBody unchunkedBody;
	
	private static AbstractBody chunkedBody;
	
	private static AbstractBody unchunkedBody2;

	@Before
	public void setUp() throws Exception {
		Arrays.fill(msg2, (byte) 20);
		unchunkedBody = new Body(new ByteArrayInputStream(msg1), msg1.length);
		unchunkedBody2 = new Body(new ByteArrayInputStream(msg2), msg2.length);
		
		chunkedBody = new ChunkedBody(new ByteArrayInputStream(msg1));
	}

	@Test
	public void testWrite() throws IOException {

		ByteArrayOutputStream out = new ByteArrayOutputStream(100);
		unchunkedBody.write(out);
		assertEquals(new String(out.toByteArray()), new String(msg1));

		ByteArrayOutputStream out2 = new ByteArrayOutputStream(10000);
		unchunkedBody2.write(out2);
		assertEquals(new String(out2.toByteArray()), new String(msg2));
	}

	@Test
	public void testGetLengthUnchunked() throws Exception {
		assertEquals(8, unchunkedBody.getLength());
		assertEquals(10000, unchunkedBody2.getLength());
	}

	@Test
	public void testChunkedBodyContent() throws Exception {
		AbstractBody body = new ChunkedBody(new ByteArrayInputStream(chunk.getBytes()));
		assertEquals(426, body.getContent().length);
		assertEquals(426, body.getLength());
	}
	
	@Test
	public void testChunkedBodyContent2() throws Exception {
		AbstractBody body = new ChunkedBody( new ByteArrayInputStream(chunk1.getBytes()));
		assertTrue(Arrays.equals(body.getContent(), chunk1Body.getBytes()));

	}
	
	@Test
	public void testChunkedBodyConten3() throws Exception {
		AbstractBody body = new ChunkedBody(new ByteArrayInputStream(chunk2.getBytes()));
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < body.getContent().length; i ++) {
			buf.append((char)body.getContent()[i]);
		}
		assertEquals(chunk2Body, buf.toString());
	}
	
	@Test
	public void testStringConstructor() throws Exception {
		AbstractBody body = new Body("mmebrane Monitor is Cool");
		assertEquals(24, body.getContent().length);
		assertEquals(24, body.getLength());
	}

}
