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
package com.predic8.membrane.core.util;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import org.brotli.dec.BrotliInputStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class MessageUtil {

	private static final Logger log = LoggerFactory.getLogger(MessageUtil.class);

	/**
	 * Returns body bytes as a stream with supported Content-Encodings (gzip, deflate, Brotli) removed.
	 * Transfer-Encodings have already been removed by the message body layer.
	 * Does not reassemble XOP/MTOM multipart packages; use {@link Message#getBodyAsStreamDecoded()}
	 * when reassembled XML is required.
	 *
	 * @see #getContent(Message)
	 */
	public static InputStream getContentAsStream(Message msg) {
		return getContentAsStream(msg.getBody(), msg.getHeader().getContentEncoding(), msg);
	}

	/**
	 * Like {@link #getContentAsStream(Message)}, for a body that is decoded on behalf of a message whose
	 * header no longer describes it, e.g. because <code>Content-Encoding</code> was already removed.
	 * <p>
	 * The encoding is a parameter, not read from <code>message</code>, because
	 * {@link com.predic8.membrane.core.http.LimitedBody} removes <code>Content-Encoding</code> from the
	 * header when it takes over the body, before anything is decoded. A decoding failure shows up only
	 * later, while the stream is read; looking the encoding up in the header then would find nothing, and
	 * the failure would no longer be reported as a {@link DecodingException} naming the encoding.
	 *
	 * @param body            the body to decode
	 * @param contentEncoding the <code>Content-Encoding</code> of the body, or <code>null</code>
	 * @param message         the message failures are associated with
	 */
	public static InputStream getContentAsStream(AbstractBody body, @Nullable String contentEncoding, Message message) {
		try {
			if ("gzip".equalsIgnoreCase(contentEncoding)) {
				return associateDecodingFailures(new GZIPInputStream(body.getContentAsStream()), contentEncoding, message);
			}
			if ("deflate".equalsIgnoreCase(contentEncoding)) {
				return associateDecodingFailures(inflating(body.getContentAsStream()), contentEncoding, message);
			}
			if ("br".equalsIgnoreCase(contentEncoding)) {
				return associateDecodingFailures(new BrotliInputStream(body.getContentAsStream()), contentEncoding, message);
			}
			return body.getContentAsStream();
		} catch (IOException e) {
			throw decodingFailure(message, contentEncoding, e);
		}
	}
	
	/**
	 * Returns the complete body bytes with the same Content-Encoding-only decoding as
	 * {@link #getContentAsStream(Message)}. Blocks until the body has been fully received.
	 * Does not reassemble XOP/MTOM: MIME boundaries and attachment parts remain in the result.
	 * Use {@link Message#getBodyAsStreamDecoded()} when reassembled XML is required.
	 */
	public static byte[] getContent(Message msg) {
		try {
			if (msg.isGzip()) {
				try (var is = msg.getBodyAsStream();
					 var decoded = new GZIPInputStream(is)) {
					return decoded.readAllBytes();
				}
			}
			if (msg.isDeflate()) {
				try (var decoded = inflating(msg.getBodyAsStream())) {
					return decoded.readAllBytes();
				}
			}
			if (msg.isBrotli()) {
				try (var is = msg.getBodyAsStream();
					 var decoded = new BrotliInputStream(is)) {
					return decoded.readAllBytes();
				}
			}
			return msg.getBody().getContent();
		} catch (IOException e) {
			throw decodingFailure(msg, msg.getHeader().getContentEncoding(), e);
		}
	}

	/**
	 * Associates a read failure with the message it happened on, naming the Content-Encoding when the
	 * message carries one. A body the sender did not label is not a decoding problem, so it keeps the
	 * plain wrapping: {@link #getContentAsStream} reads an unencoded body through the same try block.
	 * The encoding is passed in rather than read from <code>message</code>, whose header may no longer
	 * carry it; see {@link #getContentAsStream(AbstractBody, String, Message)}.
	 */
	private static ReadingBodyException decodingFailure(Message message, @Nullable String contentEncoding, IOException e) {
		if (contentEncoding == null)
			return new ReadingBodyException(e, message);
		return new ReadingBodyException(new DecodingException(contentEncoding, e), message);
	}

	/**
	 * Associates failures during reads from a streaming decoder with the original message.
	 * These failures occur after getContentAsStream has returned, outside its catch block.
	 * The association lets error handling distinguish request failures (400) from backend
	 * response failures (500), even if the encoded body has already been read successfully.
	 * Wrapping the exception does not notify body observers again: transport completion
	 * may already have released the connection.
	 * <p>
	 * Reads on the returned stream throw the unchecked {@link ReadingBodyException} where
	 * {@link com.predic8.membrane.core.http.BodyInputStream} honours the {@link InputStream}
	 * contract and throws {@link IOException}. The departure is deliberate: an IOException coming
	 * out of a read is indistinguishable from one the consumer caused itself, so consumers
	 * rediagnose it as a problem with the content. A truncated gzip body used to reach the client
	 * as "Not well-formed XML ... Premature end of file" under a security-policy header, naming the
	 * document the parser never got to read rather than the encoding that broke.
	 * <p>
	 * The cost is that a {@code catch (IOException)} around a read of this stream does not fire.
	 * Callers that handle a decoding failure themselves have to catch {@link ReadingBodyException}
	 * too, and describe it with
	 * {@link com.predic8.membrane.core.exceptions.ProblemDetails#bodyFailure} rather than deciding a
	 * status of their own; everything else lets it travel to the flow controller, which does the same.
	 */
	private static InputStream associateDecodingFailures(InputStream decoded, String contentEncoding, Message message) {
		return new FilterInputStream(decoded) {
			@Override
			public int read() {
				try {
					return in.read();
				} catch (IOException e) {
					throw decodingFailure(message, contentEncoding, e);
				}
			}

			@Override
			public int read(byte[] bytes, int offset, int length) {
				try {
					return in.read(bytes, offset, length);
				} catch (IOException e) {
					throw decodingFailure(message, contentEncoding, e);
				}
			}
		};
	}

	/**
	 * RFC 9110 8.4.1.2 defines the "deflate" coding as a zlib (RFC 1950) stream wrapping DEFLATE
	 * (RFC 1951) data; some non-conformant senders omit the zlib header and send raw DEFLATE
	 * instead. Sniff which framing was actually sent, the way browsers do, so both are accepted.
	 * A body too short to carry a zlib header (including empty) is treated as raw, preserving the
	 * existing truncation/empty-body handling below.
	 */
	static boolean isZlibWrapped(byte[] data) {
		if (data.length < 2)
			return false;
		int cmf = data[0] & 0xff;
		int flg = data[1] & 0xff;
		return (cmf & 0x0f) == 8 && ((cmf << 8) | flg) % 31 == 0;
	}

	/**
	 * Decodes the "deflate" coding while it is read, so a consumer reading through a size limit stops
	 * the expansion as soon as the limit is reached. Peeks at the first two bytes to choose between
	 * zlib and raw DEFLATE framing (see {@link #isZlibWrapped(byte[])}).
	 */
	private static InputStream inflating(InputStream compressed) throws IOException {
		final var in = new PushbackInputStream(compressed, 2);
		final var header = in.readNBytes(2);
		in.unread(header);
		return new DeflateInputStream(in, !isZlibWrapped(header));
	}

	/**
	 * Unlike its superclass, rejects a stream that ends early or asks for a preset dictionary instead of
	 * reporting the end of the body: a truncated stream would otherwise surface as a JDK message about
	 * ZLIB, and a dictionary request as a silently empty body. Membrane has no dictionary to supply.
	 */
	private static final class DeflateInputStream extends InflaterInputStream {

		DeflateInputStream(InputStream in, boolean raw) {
			super(in, new Inflater(raw));
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			final int n = super.read(b, off, len);
			if (n == -1 && inf.needsDictionary()) {
				log.info("Deflate stream requires a preset dictionary.");
				throw new IOException("Deflate stream requires a preset dictionary.");
			}
			return n;
		}

		@Override
		protected void fill() throws IOException {
			len = in.read(buf, 0, buf.length);
			if (len == -1) {
				log.info("Truncated deflate stream.");
				throw new IOException("Truncated deflate stream.");
			}
			inf.setInput(buf, 0, len);
		}

		/** The superclass only ends an Inflater it created itself. */
		@Override
		public void close() throws IOException {
			try {
				super.close();
			} finally {
				inf.end();
			}
		}
	}
}
