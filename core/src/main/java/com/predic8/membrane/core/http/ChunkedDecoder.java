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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.ByteUtil.readByteArray;
import static com.predic8.membrane.core.util.HttpUtil.MAX_LINE_LENGTH;
import static com.predic8.membrane.core.util.SystemPropertyUtil.getPositiveIntProperty;
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
        MAX_CHUNK_BYTES = getPositiveIntProperty(MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH, MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT);
    }

    private final InputStream in;
    private Header trailer;

    /**
     * Set once the last chunk and the trailer have been read; the stream then belongs to the next message.
     */
    private boolean done;

    ChunkedDecoder(InputStream in) {
        this.in = in;
    }

    /**
     * @return the next chunk, with the delimiter after it consumed, or <code>null</code> once the last
     *         chunk and the trailer have been read, on this and every later call
     * @throws IOException if reading fails or the framing is invalid
     */
    Chunk next() throws IOException {
        return next(Long.MAX_VALUE);
    }

    /**
     * Like {@link #next()}, but checks the chunk size before the chunk is read, so that a sender cannot make
     * the decoder allocate the chunk, or wait for its data, just by announcing it.
     *
     * @param maxLength the maximum length of the chunk in bytes
     * @throws ChunkTooLongException if the chunk is longer than {@code maxLength}, even if its size is beyond
     *                               what the decoder accepts as well, see {@link #readChunkSize(InputStream, long)}.
     *                               The stream is then left inside the chunk and cannot be decoded any further.
     */
    Chunk next(long maxLength) throws IOException {
        if (done) {
            return null;
        }
        final var chunkSize = readChunkSize(in, maxLength);
        if (chunkSize == 0) {
            trailer = readTrailer(in);
            done = true;
            return null;
        }
        final var chunk = new Chunk(readByteArray(in, chunkSize));
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

    /**
     * Reads the trailer section and the empty line that ends the body, RFC 9112 §7.1.2:
     * <pre><code>trailer-section = *( field-line CRLF )</code></pre>
     *
     * @return the trailer, or <code>null</code> if the body has none
     * @throws IOException if reading fails, a line does not end with CRLF or a field line is malformed
     */
    static Header readTrailer(InputStream in) throws IOException {
        return readTrailer(in, MAX_LINE_LENGTH);
    }

    /**
     * @param maxLineLength a line of this many chars or more is rejected; zero or less means no limit,
     *                      as for {@link com.predic8.membrane.core.util.HttpUtil#readLine(InputStream)}
     * @see #readTrailer(InputStream)
     */
    static Header readTrailer(InputStream in, int maxLineLength) throws IOException {
        var line = readTrailerLine(in, maxLineLength);
        if (line.isEmpty()) {
            return null;
        }
        final var trailer = new Header();
        do {
            try {
                trailer.add(Header.parseFieldLine(line, false));
            } catch (MalformedHeaderException e) {
                throw new IOException(e.getMessage(), e);
            }
        } while (!(line = readTrailerLine(in, maxLineLength)).isEmpty());
        return trailer;
    }

    /**
     * Reads a trailer line up to its CRLF. The trailer is not read with {@link com.predic8.membrane.core.util.HttpUtil#readLine(InputStream)}, which
     * accepts a bare LF as RFC 9112 §2.2 allows for the header section. Inside a chunked body every line must end
     * with CRLF like the chunk-size lines, as RFC 9112 §7.1 requires.
     *
     * @return the line without its CRLF
     */
    private static String readTrailerLine(InputStream in, int maxLineLength) throws IOException {
        final var line = new StringBuilder();
        int c;
        while ((c = read(in)) != CR) {
            if (c == LF) {
                throw invalidFraming();
            }
            line.append((char) c);
            if (maxLineLength > 0 && line.length() >= maxLineLength) {
                throw new IOException("Trailer line exceeds the limit of %d characters".formatted(maxLineLength));
            }
        }
        expect(in, LF);
        return line.toString();
    }

    /**
     * Reads a chunk-size line, RFC 9112 §7.1:
     * <pre><code>chunk-size [ chunk-ext ] CRLF
     * chunk-size = 1*HEXDIG</code></pre>
     * The chunk extensions are skipped.
     *
     * @param maxLength the maximum length of the chunk in bytes. It is checked before the decoder's own limits
     *                  on the chunk size, so that a chunk over it is reported as too long, even beyond them. The
     *                  decoder reads at most 8 hex digits, leading zeros included: a longer chunk size is reported
     *                  as too long only if the digits up to the 9th already exceed {@code maxLength}, and refused as
     *                  malformed otherwise, since its size is not known.
     * @throws ChunkTooLongException if the chunk is longer than {@code maxLength}
     */
    static int readChunkSize(InputStream in, long maxLength) throws IOException {
        final var buffer = new StringBuilder();

        int c;
        while (isHexDigit(c = read(in))) {
            if (buffer.length() >= MAX_CHUNK_SIZE_HEX_DIGITS) {
                // the chunk is at least this long, whatever digits follow
                if (Long.parseLong(buffer.toString() + (char) c, 16) > maxLength)
                    throw new ChunkTooLongException();
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

        return parseChunkSize(buffer, maxLength);
    }

    /**
     * Skips the chunk extensions up to the CR that ends the chunk-size line:
     * <pre><code>chunk-ext = *( BWS ";" BWS chunk-ext-name [ BWS "=" BWS chunk-ext-val ] )</code></pre>
     * Only the characters are checked, not the grammar of names and values, so that no line
     * terminator other than CRLF is accepted.
     *
     * Whitespace after the chunk size that is not followed by a chunk extension is skipped as well.
     *
     * @param c the first character of the chunk extensions, or the whitespace in front of them
     * @return the CR that ends the chunk-size line
     */
    private static int skipChunkExtensions(InputStream in, int c) throws IOException {
        // RFC 9112 allows whitespace (BWS) after the chunk size only before a chunk extension. Old servers
        // pad the chunk size with whitespace before the CR; that is unambiguous, so it is accepted too.
        while (c == ' ' || c == '\t') {
            c = read(in);
        }
        if (c == CR) {
            return c;
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

    /**
     * @param maxLength see {@link #readChunkSize(InputStream, long)}
     * @throws ChunkTooLongException if the chunk is longer than {@code maxLength}
     */
    static int parseChunkSize(StringBuilder buffer, long maxLength) throws IOException {
        final var hex = buffer.toString();
        if (hex.isEmpty()) {
            throw new IOException("Empty chunk-size field");
        }
        if (!hex.chars().allMatch(HexFormat::isHexDigit)) {
            // Long.parseLong would also accept a sign
            throw new IOException("Invalid chunk-size value: %s".formatted(hex));
        }
        try {
            final var size = Long.parseLong(hex, 16);
            if (size > maxLength) {
                throw new ChunkTooLongException();
            }
            if (size > MAX_CHUNK_BYTES) {
                throw new IOException("Chunk size %d exceeds limit of %d bytes".formatted(size, MAX_CHUNK_BYTES));
            }
            return (int) size;
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
        final var c = in.read();
        if (c == -1) {
            throw new EOFException("Stream ended inside a chunked body");
        }
        return c;
    }

    /**
     * A chunk is longer than the length the caller of {@link #next(long)} accepts.
     */
    static final class ChunkTooLongException extends IOException {
        ChunkTooLongException() {
            super("Chunk is longer than the maximum length.");
        }
    }

    /**
     * Correct HTTP implementations never produce invalid framing, so the exception carries no details
     * and the rejection is logged at info.
     */
    private static IOException invalidFraming() {
        log.info("Rejected invalid chunked framing.");
        return new IOException("Invalid chunked framing");
    }
}
