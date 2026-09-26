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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")
public class BodyTest {

	private static final byte[] msg0 = new byte[] { 'd', 'd', 13, 10, 13, 10 };
	private static final byte[] msg1 = new byte[] { 'd', 'd', 13, 10, 13, 10, 'f', 'r' };
	private static final byte[] msg2 = new byte[10000];
	private static final byte[] msg3 = new byte[] { 'd', 'd', 13, 13, 10 };

	private final String chunk = "1aa\r\n<?xml version='1.0' encoding='utf-8'?><soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/'><soapenv:Body><ns1:getBankResponse xmlns:ns1='http://thomas-bayer.com/blz/'><ns1:details><ns1:bezeichnung>Deutsche Bank Privat und Geschaeftskunden</ns1:bezeichnung><ns1:bic>DEUTDEDB380</ns1:bic><ns1:ort>Bonn</ns1:ort><ns1:plz>53004</ns1:plz></ns1:details></ns1:getBankResponse></soapenv:Body></soapenv:Envelope>\r\n0\r\n\r\n";

	private final String chunk1 = "7\r\naaaaaaa\r\n0\r\n\r\n";
	private final String chunk1Body = "aaaaaaa";

	private final String chunk2 = "2\r\naa\r\n3\r\nbbb\r\n0\r\n\r\n";
	private final String chunk2Body = "aabbb";

	private static AbstractBody unchunkedBody;
	private static AbstractBody unchunkedBody2;


	@BeforeAll
	public static void setUp() throws Exception {
		Arrays.fill(msg2, (byte) 20);
		unchunkedBody = new Body(new ByteArrayInputStream(msg1), msg1.length);
		unchunkedBody2 = new Body(new ByteArrayInputStream(msg2), msg2.length);
	}

	@Test
	public void testWrite() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(100);
		unchunkedBody.write(new PlainBodyTransferer(out), true);
		assertEquals(new String(msg1), out.toString());

		ByteArrayOutputStream out2 = new ByteArrayOutputStream(10000);
		unchunkedBody2.write(new PlainBodyTransferer(out2), true);
		assertEquals(new String(msg2), out2.toString());
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
		assertArrayEquals(body.getContent(), chunk1Body.getBytes());

	}

	@Test
	public void testChunkedBodyConten3() throws Exception {
		AbstractBody body = new ChunkedBody(new ByteArrayInputStream(chunk2.getBytes()));
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < body.getContent().length; i ++) {
			buf.append((char)body.getContent()[i]);
		}
		assertEquals(chunk2Body, buf.toString());
	}

	@Test
	public void testStringConstructor() throws Exception {
		AbstractBody body = new Body("mmebrane Monitor is Cool".getBytes(UTF_8));
		assertEquals(24, body.getContent().length);
		assertEquals(24, body.getLength());
	}

	@Test
	public void testPlainToChunked() throws Exception {
		Body chunked = new Body(new ByteArrayInputStream(msg1));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		chunked.write(new ChunkedBodyTransferer(baos), true);

		ChunkedBody ciob = new ChunkedBody(new ByteArrayInputStream(baos.toByteArray()));
		assertArrayEquals(msg1, ciob.getContent());
	}

	@Test
	public void testReChunked() throws Exception {
		byte[] content = chunk.getBytes(UTF_8);
		ChunkedBody ciob = new ChunkedBody(new ByteArrayInputStream(content));

		Body chunked = new Body(new ByteArrayInputStream(ciob.getContent()));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		chunked.write(new ChunkedBodyTransferer(baos), true);

		ChunkedBody ciob2 = new ChunkedBody(new ByteArrayInputStream(baos.toByteArray()));

		assertArrayEquals(ciob2.getContent(), ciob.getContent());
	}

	@Test
	void getContentThrowsWhenBodyExceedsArrayLimit() {
		AbstractBody body = bodyReporting((long) Integer.MAX_VALUE + 1);
		BodyTooLargeException e = assertThrows(BodyTooLargeException.class, body::getContent);
		assertInstanceOf(ReadingBodyException.class, e); // stays catchable as a generic body-read failure
	}

	@Test
	void largeBodyLengthRoundTripsThroughContentLength() {
		// Regression: getLength() used to be int and overflowed >2GB to a negative value,
		// which setContentLength wrote as e.g. "-2147483648" and getContentLength then rejected.
		long huge = (long) Integer.MAX_VALUE + 1024;
		Header header = new Header();
		header.setContentLength(bodyReporting(huge).getLength());
		assertEquals(huge, header.getContentLength());
	}

	/** A body that only reports a length, without allocating it — for boundary tests. */
	private static AbstractBody bodyReporting(long length) {
		return new AbstractBody() {
			@Override public long getLength() { return length; }
			@Override protected void readLocal() {}
			@Override protected void writeAlreadyRead(AbstractBodyTransferer out) {}
			@Override protected void writeStreamed(AbstractBodyTransferer out) {}
			@Override protected byte[] getRawLocal() { return new byte[0]; }
		};
	}

	@Test
	void hasRelevantObservers() {
		assertFalse(unchunkedBody.hasRelevantObservers());
		unchunkedBody.addObserver(new NonRelevantObserver());
		assertFalse(unchunkedBody.hasRelevantObservers());
		unchunkedBody.addObserver(new AbstractMessageObserver() {});
		assertTrue(unchunkedBody.hasRelevantObservers());
	}

	/**
	 * A sender that announces Content-Length: 7 and then delivers 4 bytes must not produce a body
	 * that claims to be complete. readByteArray() pads the shortfall with NULs, so the truncated
	 * upload is reported as fully read and forwarded to the backend.
	 * See <a href="https://github.com/membrane/api-gateway/issues/3193">#3193</a>.
	 */
	@Test
	void truncatedContentLengthBodyFailsInsteadOfBeingZeroPadded() {
		Body truncated = new Body(new ByteArrayInputStream("part".getBytes()), 7);

		ReadingBodyException e = assertThrows(ReadingBodyException.class, truncated::read);

		assertInstanceOf(EOFException.class, e.getCause());
		assertFalse(truncated.isRead());
	}

	@Test
	void completeContentLengthBodyIsStillRead() {
		Body complete = new Body(new ByteArrayInputStream("payload".getBytes()), 7);

		complete.read();

		assertTrue(complete.isRead());
		assertArrayEquals("payload".getBytes(), complete.getContent());
	}

	/**
	 * With a relevant observer attached, discard() reads into an 8 KiB buffer without capping the
	 * read at Content-Length, consuming the bytes that follow the body on the stream.
	 * See <a href="https://github.com/membrane/api-gateway/issues/3341">#3341</a>.
	 */
	@Test
	void discardWithObserverDoesNotReadPastContentLength() {
		var in = new ByteArrayInputStream("1234567NEXT".getBytes());
		Body body = new Body(in, 7);
		ByteArrayOutputStream observed = new ByteArrayOutputStream();
		body.addObserver(recordingObserver(observed));

		body.discard();

		assertEquals("1234567", observed.toString());
		assertEquals(4, in.available());
	}

	/** Same as above for the streamed write path: the transferer must not get the trailing bytes. */
	@Test
	void writeStreamedDoesNotReadPastContentLength() {
		ByteArrayInputStream in = new ByteArrayInputStream("1234567NEXT".getBytes());
		Body body = new Body(in, 7);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		body.write(new PlainBodyTransferer(out), false);

		assertEquals("1234567", out.toString());
		assertEquals(4, in.available());
	}

	/** End-to-end: a pipelined second request on a keep-alive stream survives discarding the first body. */
	@Test
	void pipelinedRequestSurvivesDiscardOfPreviousBodyWithObserver() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(("""
				POST /first HTTP/1.1\r
				Host: example.com\r
				Content-Length: 7\r
				\r
				1234567\
				GET /second HTTP/1.1\r
				Host: example.com\r
				\r
				""").getBytes(US_ASCII));

		Request first = new Request();
		first.read(in, true);
		first.getBody().addObserver(new AbstractMessageObserver() {});
		first.getBody().discard();

		Request second = new Request();
		second.read(in, true);
		assertEquals("/second", second.getUri());
	}

	private static MessageObserver recordingObserver(ByteArrayOutputStream sink) {
		return new AbstractMessageObserver() {
			@Override
			public void bodyChunk(byte[] buffer, int offset, int length) {
				sink.write(buffer, offset, length);
			}
		};
	}

	private static class NonRelevantObserver extends AbstractMessageObserver implements NonRelevantBodyObserver {}

}
