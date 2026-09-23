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

import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.DecodingException;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

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
		try {
			if (msg.isGzip()) {
				return associateDecodingFailures(new GZIPInputStream(msg.getBodyAsStream()), msg);
			}
			if (msg.isDeflate()) {
				return new ByteArrayInputStream(getDecompressedData(msg.getBody().getContent()));
			}
			if (msg.isBrotli()) {
				return associateDecodingFailures(new BrotliInputStream(msg.getBodyAsStream()), msg);
			}
			return msg.getBodyAsStream();
		} catch (IOException e) {
			throw decodingFailure(msg, e);
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
				return getDecompressedData(msg.getBody().getContent());
			}
			if (msg.isBrotli()) {
				try (var is = msg.getBodyAsStream();
					 var decoded = new BrotliInputStream(is)) {
					return decoded.readAllBytes();
				}
			}
			return msg.getBody().getContent();
		} catch (IOException e) {
			throw decodingFailure(msg, e);
		}
	}

	/**
	 * Associates a read failure with the message it happened on, naming the Content-Encoding when the
	 * message carries one. A body the sender did not label is not a decoding problem, so it keeps the
	 * plain wrapping: {@link #getContentAsStream} reads an unencoded body through the same try block.
	 */
	private static ReadingBodyException decodingFailure(Message message, IOException e) {
		final String contentEncoding = message.getHeader().getContentEncoding();
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
	private static InputStream associateDecodingFailures(InputStream decoded, Message message) {
		return new FilterInputStream(decoded) {
			@Override
			public int read() {
				try {
					return in.read();
				} catch (IOException e) {
					throw decodingFailure(message, e);
				}
			}

			@Override
			public int read(byte[] bytes, int offset, int length) {
				try {
					return in.read(bytes, offset, length);
				} catch (IOException e) {
					throw decodingFailure(message, e);
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

	public static byte[] getDecompressedData(byte[] compressedData) throws IOException {
		var decompressor = new Inflater(!isZlibWrapped(compressedData));
		try {
			decompressor.setInput(compressedData);

			List<Chunk> chunks = new ArrayList<>();

			while (!decompressor.finished()) {
				byte[] buf = new byte[1024];
				int count;
				try {
					count = decompressor.inflate(buf);
				} catch (DataFormatException e) {
					throw new IOException(e);
				}
				// Zero output is valid when the stream has finished (including an empty body).
				// Otherwise, retrying without new input or a dictionary can loop forever.
				if (count == 0 && !decompressor.finished()) {
					// A preset dictionary contains bytes shared by compressor and decoder in advance.
					// Only a zlib-framed stream can signal this (via the FDICT header bit); Membrane
					// does not supply one, so reject rather than stall waiting for it.
					if (decompressor.needsDictionary()) {
						log.info("Deflate stream requires a preset dictionary.");
						throw new IOException("Deflate stream requires a preset dictionary.");
					}
					// The complete compressed body was supplied, so no more input can arrive.
					if (decompressor.needsInput()) {
						log.info("Truncated deflate stream.");
						throw new IOException("Truncated deflate stream.");
					}
					// Reject any other stalled state instead of retrying indefinitely.
					log.info("Deflate decompression made no progress.");
					throw new IOException("Deflate decompression made no progress.");
				}
				if (buf.length == count) {
                    chunks.add(new Chunk(buf));
				} else if (count < buf.length) {
					byte[] shortContent = new byte[count];
					System.arraycopy(buf, 0, shortContent, 0, count);
                    chunks.add(new Chunk(shortContent));
				}
			}

			log.debug("Number of decompressed chunks: {}", chunks.size());
			if (!chunks.isEmpty()) {

				var bos = new ByteArrayOutputStream();

				for (Chunk chunk : chunks) {
					bos.write(chunk.content());
				}
				return bos.toByteArray();
			}
			return null;
		} finally {
			// Inflater is not AutoCloseable in Java 21, so use finally instead of try-with-resources.
			decompressor.end();
		}
	}
}
