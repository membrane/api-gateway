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
import java.io.InputStream;
import java.util.Arrays;

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
	 * A body of unknown length (EOF-delimited, length -1) must be drained to EOF by discard(),
	 * not just marked as read with its bytes left in the stream.
	 * See <a href="https://github.com/membrane/api-gateway/issues/3330">#3330</a>.
	 */
	@Test
	void discardDoesNotReadABodyOfUnknownLength() {
		Body body = new Body(failOnReadStream());

		body.discard();

		assertTrue(body.hasFailed(), "a body that was not drained must not count as read");
		assertFalse(body.isRead());
	}

	/**
	 * The point of the rule, rather than its symptom: a stream that never reaches EOF must not be read
	 * at all. A ByteArrayInputStream would end instantly and hide a reintroduced drain.
	 */
	private static InputStream failOnReadStream() {
		return new InputStream() {
			@Override
			public int read() {
				throw new AssertionError("discard() must not read a body of unknown length");
			}

			@Override
			public int read(byte[] b, int off, int len) {
				throw new AssertionError("discard() must not read a body of unknown length");
			}
		};
	}

	@Test
	void discardLeavesTheStreamOfAnUnknownLengthBodyUntouched() {
		ByteArrayInputStream in = new ByteArrayInputStream("leftover bytes".getBytes(UTF_8));

		new Body(in).discard();

		assertEquals("leftover bytes".length(), in.available(), "nothing may be consumed");
	}

	@Test
	void discardOfUnknownLengthBodyReportsFailureToObservers() {
		Body body = new Body(failOnReadStream());
		RecordingObserver observer = new RecordingObserver();
		body.addObserver(observer);

		body.discard();

		assertTrue(observer.failed, "bodyFailed is the terminal event here");
		assertFalse(observer.completed, "bodyComplete must not follow bodyFailed");
		assertEquals("", observer.observed.toString(UTF_8), "no chunk may be handed out");
	}

	@Test
	void discardDrainsABodyOfKnownLengthAndNotifiesRelevantObserver() throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream("leftover bytes".getBytes(UTF_8));
		Body body = new Body(in, "leftover bytes".length());
		RecordingObserver observer = new RecordingObserver();
		body.addObserver(observer);

		body.discard();

		assertTrue(body.isRead());
		assertEquals(0, in.available(), "discard() must consume the declared bytes");
		assertEquals("leftover bytes", observer.observed.toString(UTF_8));
		assertTrue(observer.completed);
		assertFalse(observer.failed);
	}

	/**
	 * The bytes after the declared length belong to the next message on the connection; consuming them
	 * would desync it.
	 */
	@Test
	void discardDoesNotReadPastTheDeclaredLength() throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream("bodyNEXT-REQUEST".getBytes(UTF_8));
		Body body = new Body(in, 4);

		body.discard();

		assertTrue(body.isRead());
		assertEquals("NEXT-REQUEST".length(), in.available(), "only the body itself may be consumed");
	}

	/**
	 * Declared ten bytes, four arrived and the stream ended. The connection is out of step, so the body
	 * must not be passed off as read. A read timeout reaches this the same way, as an IOException.
	 */
	@Test
	void discardFailsWhenAKnownLengthBodyEndsEarly() {
		Body body = new Body(new ByteArrayInputStream("four".getBytes(UTF_8)), 10);

		body.discard();

		assertTrue(body.hasFailed());
		assertFalse(body.isRead());
	}

	@Test
	void discardOfKnownLengthBodySkipsChunksForNonRelevantObserverButStillCompletes() throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream("leftover bytes".getBytes(UTF_8));
		Body body = new Body(in, "leftover bytes".length());
		body.addObserver(new NonRelevantObserver());

		body.discard();

		assertTrue(body.isRead());
		assertEquals(0, in.available());
	}

	private static class RecordingObserver extends AbstractMessageObserver {
		final ByteArrayOutputStream observed = new ByteArrayOutputStream();
		boolean completed;
		boolean failed;

		@Override
		public void bodyChunk(byte[] buffer, int offset, int length) {
			observed.write(buffer, offset, length);
		}

		@Override
		public void bodyComplete(AbstractBody body) {
			completed = true;
		}

		@Override
		public void bodyFailed(ReadingBodyException e) {
			failed = true;
		}
	}

	private static class NonRelevantObserver extends AbstractMessageObserver implements NonRelevantBodyObserver {}

}
