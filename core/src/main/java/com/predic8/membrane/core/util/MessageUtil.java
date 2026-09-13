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
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
				return new GZIPInputStream(msg.getBodyAsStream());
			}
			if (msg.isDeflate()) {
				return new ByteArrayInputStream(getDecompressedData(msg.getBody().getContent()));
			}
			if (msg.isBrotli()) {
				return new BrotliInputStream(msg.getBodyAsStream());
			}
			return msg.getBodyAsStream();
		} catch (IOException e) {
			throw new ReadingBodyException(e);
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
			throw new ReadingBodyException(e);
		}
	}

	public static byte[] getDecompressedData(byte[] compressedData) throws IOException {
		var decompressor = new Inflater(true);
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
					// Defensive check: raw deflate has no header identifying a required dictionary.
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
