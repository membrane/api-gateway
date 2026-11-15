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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Stream;

import static com.predic8.membrane.annot.Constants.CRLF;
import static com.predic8.membrane.annot.Constants.MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT;
import static com.predic8.membrane.core.http.ChunkedDecoder.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class ChunkedDecoderTest {

    @Test
    void decodesChunksAndReadsNothingAfterTheBody() throws IOException {
        final var in = stream("3\r\nabc\r\n10\r\n0123456789abcdef\r\n0\r\n\r\nNEXT");
        final var decoder = new ChunkedDecoder(in);

        assertEquals("abc", text(decoder.next()));
        assertEquals("0123456789abcdef", text(decoder.next()));
        assertNull(decoder.next());
        assertNull(decoder.getTrailer());
        assertEquals("NEXT", new String(in.readAllBytes(), US_ASCII), "the next message must stay in the stream");
    }

    @Test
    void chunkExtensionIsIgnored() throws IOException {
        final var decoder = new ChunkedDecoder(stream("3;name=value\r\nabc\r\n0\r\n\r\n"));

        assertEquals("abc", text(decoder.next()));
        assertNull(decoder.next());
    }

    @Test
    void trailerIsReadAfterTheLastChunk() throws IOException {
        final var in = stream("3\r\nabc\r\n0\r\nX-Checksum: 42\r\n\r\nNEXT");
        final var decoder = new ChunkedDecoder(in);

        assertEquals("abc", text(decoder.next()));
        assertNull(decoder.getTrailer(), "the trailer is only known at the end");
        assertNull(decoder.next());
        assertEquals("42", decoder.getTrailer().getFirstValue("X-Checksum"));
        assertEquals("NEXT", new String(in.readAllBytes(), US_ASCII));
    }

    @Test
    void oversizedChunkIsRejected() {
        final var decoder = new ChunkedDecoder(stream("7FFFFFFF\r\n"));

        assertThrows(IOException.class, decoder::next);
    }

    @Test
    void testReadChunkSize() throws Exception {
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(("3d2F" + CRLF).getBytes())));
    }

    @Test
    void testReadChunkSizeWithExtension() throws Exception {
        String s = "3d2F" + CRLF + ";gfgfgfg" + CRLF;
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(s.getBytes())));
    }

    @Test
    void testReadChunkSizeWithExtensionValue() throws Exception {
        String s = "3d2F" + CRLF + ";gfgf=gfg" + CRLF;
        assertEquals(15663, readChunkSize(new ByteArrayInputStream(s.getBytes())));
    }

    @Test
    void readTrailerTest() throws IOException {
        byte[] trailer = ("3D" + CRLF + CRLF).getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(trailer);
        assertEquals(61, readChunkSize(is)); // 3D = 61
        readTrailer(is);
        assertEquals(0, is.available()); // Check that everything is read
    }

    private static ByteArrayInputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(US_ASCII));
    }

    private static String text(Chunk chunk) {
        return new String(chunk.content(), US_ASCII);
    }

    // -------------------------------------------------------------------------
    // parseChunkSize unit tests
    // -------------------------------------------------------------------------

    @Nested
    class ParseChunkSizeTest {

        // -- valid inputs ------------------------------------------------------

        @ParameterizedTest
        @MethodSource("validChunkSizes")
        void validInputsParseCorrectly(String input, int expected) throws IOException {
            assertEquals(expected, parseChunkSize(buf(input)));
        }

        static Stream<Arguments> validChunkSizes() {
            return Stream.of(
                    Arguments.of("0",    0),          // terminating chunk
                    Arguments.of("1",    1),
                    Arguments.of("ff",   255),        // lowercase hex
                    Arguments.of("FF",   255),        // uppercase hex
                    Arguments.of("3d2F", 15663),      // mixed case
                    Arguments.of("  a  ", 10),        // whitespace trimmed
                    Arguments.of(Integer.toHexString(MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT),
                                 MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT)  // exactly at limit
            );
        }

        // -- too large --------------------------------------------------------

        @Test
        void oneByteOverLimitIsRejected() {
            assertThrows(IOException.class,
                    () -> parseChunkSize(buf(Integer.toHexString(MEMBRANE_CORE_HTTP_BODY_MAXCHUNKLENGTH_DEFAULT + 1))));
        }

        @Test
        void integerMaxValueIsRejected() {
            // 7FFFFFFF = Integer.MAX_VALUE = 2 GiB — parses without NumberFormatException but exceeds cap
            assertThrows(IOException.class, () -> parseChunkSize(buf("7fffffff")));
        }

        @Test
        void overflowsIntegerParseIsRejectedAsInvalidValue() {
            // 80000000 exceeds Integer.MAX_VALUE → Integer.parseInt throws NumberFormatException
            assertThrows(IOException.class, () -> parseChunkSize(buf("80000000")));
        }

        @Test
        void maxUnsignedHexIsRejectedAsInvalidValue() {
            assertThrows(IOException.class, () -> parseChunkSize(buf("ffffffff")));
        }

        // -- negative (sign prefix accepted by Integer.parseInt) --------------

        @Test
        void negativeValueIsRejected() {
            assertThrows(IOException.class, () -> parseChunkSize(buf("-1")));
        }

        // -- empty / blank ----------------------------------------------------

        @Test
        void emptyBufferIsRejected() {
            assertThrows(IOException.class, () -> parseChunkSize(buf("")));
        }

        @Test
        void blankBufferIsRejected() {
            assertThrows(IOException.class, () -> parseChunkSize(buf("   ")));
        }

        // -- non-hex characters -----------------------------------------------

        @ParameterizedTest
        @ValueSource(strings = {"xyz", "1g", "0x1a", "12 34", "1.0"})
        void nonHexInputIsRejected(String input) {
            assertThrows(IOException.class, () -> parseChunkSize(buf(input)));
        }

        // -- helper -----------------------------------------------------------

        private static StringBuilder buf(String s) {
            return new StringBuilder(s);
        }
    }

}
