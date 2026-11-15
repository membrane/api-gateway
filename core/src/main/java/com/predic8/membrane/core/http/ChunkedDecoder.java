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

import java.io.IOException;
import java.io.InputStream;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.util.ByteUtil.readByteArray;

/**
 * Decodes the framing of a body with "Transfer-Encoding: chunked": chunk sizes, chunk extensions, the
 * delimiters and the trailer. What happens to the decoded chunks, whether they are retained,
 * forwarded or dropped, is up to the caller, see {@link ChunkedBody}.
 * <p>
 * Reads exactly the body from the stream and nothing after it, as the next message on the connection
 * may follow.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
final class ChunkedDecoder {

    private static final Logger log = LoggerFactory.getLogger(ChunkedDecoder.class);

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
        in.read(); // CR
        in.read(); // LF
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
        in.mark(2);
        if (in.read() == 13) {
            in.read(); // LF
            return null;
        }
        in.reset();
        try {
            return new Header(in);
        } catch (EndOfStreamException e) {
            throw new IOException(e);
        }
    }

    static int readChunkSize(InputStream in) throws IOException {
        var buffer = new StringBuilder();

        int c;
        while ((c = in.read()) != -1) {
            if (c == 13) { // CR — end of chunk-size line
                in.read(); // consume LF
                break;
            }

            // chunk-ext: ignore everything from ';' through the terminating LF
            if (c == ';') {
                //noinspection StatementWithEmptyBody
                while ((c = in.read()) != 10 && c != -1)
                    ;
                break; // LF consumed; chunk-size line is done
            }

            if (buffer.length() >= MAX_CHUNK_SIZE_HEX_DIGITS) {
                log.info("Chunk-size {} exceeds limit: {}",buffer.length(), MAX_CHUNK_SIZE_HEX_DIGITS);
                throw new IOException("Chunk-size exceeds limit");
            }
            buffer.append((char) c);
        }

        return parseChunkSize(buffer);
    }

    static int parseChunkSize(StringBuilder buffer) throws IOException {
        var hex = buffer.toString().trim();
        if (hex.isEmpty()) {
            throw new IOException("Empty chunk-size field");
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
}
