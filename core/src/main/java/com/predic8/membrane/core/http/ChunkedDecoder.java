/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.util.EndOfStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.ByteUtil.readByteArray;
import static java.util.HexFormat.isHexDigit;

/**
 * Decodes the framing of a body with "Transfer-Encoding: chunked": chunk sizes, chunk extensions, the
 * delimiters and the trailer. What happens to the decoded chunks, whether they are retained,
 * forwarded or dropped, is up to the caller, see {@link ChunkedBody}.
 * <p>
 * Reads exactly the body from the stream and nothing after it, as the next message on the connection
 * may follow.
 */
final class ChunkedDecoder {

    private static final Logger log = LoggerFactory.getLogger(ChunkedDecoder.class);

    private static final int CR = '\r';
    private static final int LF = '\n';

    /**
     * Maximum hex digits in a chunk-size field. Prevents Long.parseLong from receiving a
     * value that overflows a long (would require >8 digits), with a margin.
     */
    private static final int MAX_CHUNK_SIZE_HEX_DIGITS = 8;

    /**
     * Hard cap on a single chunk in bytes
     * Without this, a sender can trigger a 2 GiB byte[] allocation with just "7FFFFFFF\r\n"
     */
    private static int MAX_CHUNK_BYTES;

    static {
        var v = System.getProperty(MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH);
        MAX_CHUNK_BYTES = v == null ? MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT : Integer.parseInt(v);
    }

    private final InputStream in;
    private Header trailer;

    ChunkedDecoder(InputStream in) {
        this.in = in;
    }

    /**
     * @return the next chunk, with the delimiter after it consumed, or <code>null</code> once the last
     *         chunk and the trailer have been read
     * @throws IOException if reading fails or the framing is invalid
     */
    Chunk next() throws IOException {
        int chunkSize = readChunkSize(in);
        if (chunkSize == 0) {
            trailer = readTrailer(in);
            return null;
        }
        var chunk = new Chunk(readByteArray(in, chunkSize));
        expect(in, CR);
        expect(in, LF);
        return chunk;
    }

    /**
     * @return the trailer, or <code>null</code> if the body has none or {@link #next()} has not
     *         reached the end yet
     */
    Header getTrailer() {
        return trailer;
    }

    static Header readTrailer(InputStream in) throws IOException {
        in.mark(1);
        var c = read(in);
        if (c == CR) {
            expect(in, LF);
            return null;
        }
        if (c == LF) {
            throw invalidFraming();
        }
        in.reset();
        try {
            return new Header(in);
        } catch (EndOfStreamException e) {
            throw new IOException(e);
        }
    }

    /**
     * Reads a chunk-size line, RFC 9112 §7.1:
     * <pre><code>chunk-size [ chunk-ext ] CRLF
     * chunk-size = 1*HEXDIG</code></pre>
     * The chunk extensions are skipped.
     */
    static int readChunkSize(InputStream in) throws IOException {
        var buffer = new StringBuilder();

        int c;
        while (isHexDigit(c = read(in))) {
            if (buffer.length() >= MAX_CHUNK_SIZE_HEX_DIGITS) {
                log.info("Chunk-size {} exceeds limit: {}",buffer.length(), MAX_CHUNK_SIZE_HEX_DIGITS);
                throw new IOException("Chunk-size exceeds limit");
            }
            buffer.append((char) c);
        }

        if (c == ' ' || c == '\t' || c == ';') {
            c = skipChunkExtensions(in, c);
        }
        if (c != CR) {
            throw invalidFraming();
        }
        expect(in, LF);

        return parseChunkSize(buffer);
    }

    /**
     * Skips the chunk extensions up to the CR that ends the chunk-size line:
     * <pre><code>chunk-ext = *( BWS ";" BWS chunk-ext-name [ BWS "=" BWS chunk-ext-val ] )</code></pre>
     * Only the characters are checked, not the grammar of names and values, so that no line
     * terminator other than CRLF is accepted.
     *
     * @param c the first character of the chunk extensions
     * @return the CR that ends the chunk-size line
     */
    private static int skipChunkExtensions(InputStream in, int c) throws IOException {
        // Whitespace (BWS) after the chunk size is only allowed before a chunk extension
        while (c == ' ' || c == '\t') {
            c = read(in);
        }
        if (c != ';') {
            throw invalidFraming();
        }
        while ((c = read(in)) != CR) {
            // HTAB, VCHAR, SP and obs-text are the characters of tokens and quoted-strings
            if (c != '\t' && (c < 0x20 || c == 0x7F)) {
                throw invalidFraming();
            }
        }
        return c;
    }

    static int parseChunkSize(StringBuilder buffer) throws IOException {
        var hex = buffer.toString();
        if (hex.isEmpty()) {
            throw new IOException("Empty chunk-size field");
        }
        if (!hex.chars().allMatch(HexFormat::isHexDigit)) {
            // Integer.parseInt would also accept a sign
            throw new IOException("Invalid chunk-size value: %s".formatted(hex));
        }
        try {
            int size = Integer.parseInt(hex, 16);
            if (size < 0 || size > MAX_CHUNK_BYTES) {
                throw new IOException("Chunk size %d exceeds limit of %d bytes".formatted(size, MAX_CHUNK_BYTES));
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid chunk-size value: %s".formatted(hex), e);
        }
    }

    private static void expect(InputStream in, int expected) throws IOException {
        if (read(in) != expected) {
            throw invalidFraming();
        }
    }

    /**
     * @throws EOFException if the stream ends, as it always ends inside the body
     */
    private static int read(InputStream in) throws IOException {
        var c = in.read();
        if (c == -1) {
            throw new EOFException("Stream ended inside a chunked body");
        }
        return c;
    }

    /**
     * HTTP libraries frame chunks correctly, so invalid framing points to an attack rather than to a
     * mistake a sender could fix: the exception carries no details, and the attempt is logged at
     * info like other attack detections.
     */
    private static IOException invalidFraming() {
        log.info("Rejected invalid chunked framing, possibly an HTTP request smuggling attempt.");
        return new IOException("Invalid chunked framing");
    }
}
